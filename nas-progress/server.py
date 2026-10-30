#!/usr/bin/env python3
"""LAN-only progress sync API for Le&Sil Stream. Uses only Python's standard library."""
import hmac
import json
import os
import re
import sqlite3
import time
from contextlib import closing
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

TOKEN = os.environ.get("PROGRESS_SYNC_TOKEN", "")
DB_PATH = os.environ.get("PROGRESS_DB_PATH", "/data/progress.sqlite3")
PORT = int(os.environ.get("PORT", "8787"))
PROFILE_RE = re.compile(r"^[a-f0-9]{64}$")
MEDIA_RE = re.compile(r"^(?:[0-9]{1,20}|episode:[0-9]{1,20})$")

if len(TOKEN) < 32:
    raise SystemExit("PROGRESS_SYNC_TOKEN precisa ter pelo menos 32 caracteres")


def connect_db():
    connection = sqlite3.connect(DB_PATH, timeout=10)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA journal_mode=WAL")
    connection.execute("""
        CREATE TABLE IF NOT EXISTS progress (
            profile_id TEXT NOT NULL,
            media_id TEXT NOT NULL,
            title TEXT NOT NULL,
            poster_url TEXT,
            position_ms INTEGER NOT NULL,
            duration_ms INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            completed INTEGER NOT NULL DEFAULT 0,
            hidden_from_continue INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY (profile_id, media_id)
        )
    """)
    if "hidden_from_continue" not in {row[1] for row in connection.execute("PRAGMA table_info(progress)")}:
        try:
            connection.execute("ALTER TABLE progress ADD COLUMN hidden_from_continue INTEGER NOT NULL DEFAULT 0")
        except sqlite3.OperationalError as error:
            if "duplicate column name" not in str(error).lower():
                raise
    connection.execute("""
        CREATE TABLE IF NOT EXISTS favorites (
            profile_id TEXT NOT NULL,
            media_type TEXT NOT NULL,
            media_id INTEGER NOT NULL,
            title TEXT NOT NULL,
            poster_url TEXT,
            updated_at INTEGER NOT NULL,
            active INTEGER NOT NULL DEFAULT 1,
            PRIMARY KEY (profile_id, media_type, media_id)
        )
    """)
    return connection


class Handler(BaseHTTPRequestHandler):
    server_version = "LeSilProgress/1.0"

    def log_message(self, _format, *_args):
        # Request URLs and payloads can identify watched media; keep logs quiet by default.
        return

    def send_json(self, status, body):
        encoded = json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(encoded)

    def authorized(self):
        supplied = self.headers.get("Authorization", "")
        expected = "Bearer " + TOKEN
        return hmac.compare_digest(supplied.encode("utf-8"), expected.encode("utf-8"))

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self.send_json(200, {"status": "ok"})
            return
        if parsed.path not in ("/api/v1/progress", "/api/v1/favorites"):
            self.send_json(404, {"error": "not_found"})
            return
        if not self.authorized():
            self.send_json(401, {"error": "unauthorized"})
            return
        profile_id = parse_qs(parsed.query).get("profile_id", [""])[0]
        if not PROFILE_RE.fullmatch(profile_id):
            self.send_json(400, {"error": "invalid_profile_id"})
            return
        with closing(connect_db()) as db, db:
            if parsed.path == "/api/v1/progress":
                rows = db.execute(
                    "SELECT media_id,title,poster_url,position_ms,duration_ms,updated_at,completed,hidden_from_continue "
                    "FROM progress WHERE profile_id=? ORDER BY updated_at DESC LIMIT 5000",
                    (profile_id,),
                ).fetchall()
            else:
                rows = db.execute(
                    "SELECT media_type,media_id,title,poster_url,updated_at,active "
                    "FROM favorites WHERE profile_id=? ORDER BY updated_at DESC LIMIT 5000",
                    (profile_id,),
                ).fetchall()
        self.send_json(200, {"items": [dict(row) for row in rows]})

    def do_PUT(self):
        path = urlparse(self.path).path
        if path not in ("/api/v1/progress", "/api/v1/favorites"):
            self.send_json(404, {"error": "not_found"})
            return
        if not self.authorized():
            self.send_json(401, {"error": "unauthorized"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 16384:
                raise ValueError("invalid_body_size")
            item = json.loads(self.rfile.read(length))
            profile_id = str(item.get("profile_id", ""))
            title = str(item.get("title", "Sem título")).strip()[:500] or "Sem título"
            poster_url = item.get("poster_url")
            if poster_url is not None:
                poster_url = str(poster_url)[:2048]
                if not poster_url.startswith(("http://", "https://")):
                    poster_url = None
            updated_at = max(0, min(int(item.get("updated_at", int(time.time() * 1000))), int(time.time() * 1000) + 300000))
            if not PROFILE_RE.fullmatch(profile_id):
                raise ValueError("invalid_id")
            if path == "/api/v1/progress":
                media_id = str(item.get("media_id", ""))
                if not MEDIA_RE.fullmatch(media_id):
                    raise ValueError("invalid_id")
                position_ms = max(0, min(int(item.get("position_ms", 0)), 7 * 24 * 60 * 60 * 1000))
                duration_ms = max(0, min(int(item.get("duration_ms", 0)), 7 * 24 * 60 * 60 * 1000))
                completed = 1 if bool(item.get("completed", False)) else 0
                hidden_from_continue = 1 if bool(item.get("hidden_from_continue", False)) else 0
            else:
                media_type = str(item.get("media_type", ""))
                media_id = int(item.get("media_id", 0))
                if media_type not in ("movie", "series") or not 0 < media_id <= 2**31 - 1:
                    raise ValueError("invalid_id")
                active = 1 if bool(item.get("active", False)) else 0
        except (ValueError, TypeError, json.JSONDecodeError):
            self.send_json(400, {"error": "invalid_payload"})
            return

        with closing(connect_db()) as db, db:
            if path == "/api/v1/progress":
                db.execute("""
                    INSERT INTO progress(profile_id,media_id,title,poster_url,position_ms,duration_ms,updated_at,completed,hidden_from_continue)
                    VALUES(?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(profile_id,media_id) DO UPDATE SET
                        title=excluded.title, poster_url=excluded.poster_url,
                        position_ms=excluded.position_ms, duration_ms=excluded.duration_ms,
                        updated_at=excluded.updated_at, completed=excluded.completed,
                        hidden_from_continue=excluded.hidden_from_continue
                    WHERE excluded.updated_at >= progress.updated_at
                """, (profile_id, media_id, title, poster_url, position_ms, duration_ms,
                      updated_at, completed, hidden_from_continue))
            else:
                db.execute("""
                    INSERT INTO favorites(profile_id,media_type,media_id,title,poster_url,updated_at,active)
                    VALUES(?,?,?,?,?,?,?)
                    ON CONFLICT(profile_id,media_type,media_id) DO UPDATE SET
                        title=excluded.title, poster_url=excluded.poster_url,
                        updated_at=excluded.updated_at, active=excluded.active
                    WHERE excluded.updated_at >= favorites.updated_at
                """, (profile_id, media_type, media_id, title, poster_url, updated_at, active))
        self.send_json(200, {"status": "saved"})

    def do_OPTIONS(self):
        self.send_json(405, {"error": "method_not_allowed"})


if __name__ == "__main__":
    connect_db().close()
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
