#!/usr/bin/env python3
"""LAN-only progress sync API for Le&Sil Stream. Uses only Python's standard library."""
import hmac
import json
import os
import re
import sqlite3
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

TOKEN = os.environ.get("PROGRESS_SYNC_TOKEN", "")
DB_PATH = os.environ.get("PROGRESS_DB_PATH", "/data/progress.sqlite3")
PORT = int(os.environ.get("PORT", "8787"))
PROFILE_RE = re.compile(r"^[a-f0-9]{64}$")
MEDIA_RE = re.compile(r"^[0-9]{1,20}$")

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
            PRIMARY KEY (profile_id, media_id)
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
        if parsed.path != "/api/v1/progress":
            self.send_json(404, {"error": "not_found"})
            return
        if not self.authorized():
            self.send_json(401, {"error": "unauthorized"})
            return
        profile_id = parse_qs(parsed.query).get("profile_id", [""])[0]
        if not PROFILE_RE.fullmatch(profile_id):
            self.send_json(400, {"error": "invalid_profile_id"})
            return
        with connect_db() as db:
            rows = db.execute(
                "SELECT media_id,title,poster_url,position_ms,duration_ms,updated_at,completed "
                "FROM progress WHERE profile_id=? ORDER BY updated_at DESC LIMIT 500",
                (profile_id,),
            ).fetchall()
        self.send_json(200, {"items": [dict(row) for row in rows]})

    def do_PUT(self):
        if urlparse(self.path).path != "/api/v1/progress":
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
            media_id = str(item.get("media_id", ""))
            title = str(item.get("title", "Sem título")).strip()[:500] or "Sem título"
            poster_url = item.get("poster_url")
            if poster_url is not None:
                poster_url = str(poster_url)[:2048]
                if not poster_url.startswith(("http://", "https://")):
                    poster_url = None
            position_ms = max(0, min(int(item.get("position_ms", 0)), 7 * 24 * 60 * 60 * 1000))
            duration_ms = max(0, min(int(item.get("duration_ms", 0)), 7 * 24 * 60 * 60 * 1000))
            updated_at = max(0, min(int(item.get("updated_at", int(time.time() * 1000))), int(time.time() * 1000) + 300000))
            completed = 1 if bool(item.get("completed", False)) else 0
            if not PROFILE_RE.fullmatch(profile_id) or not MEDIA_RE.fullmatch(media_id):
                raise ValueError("invalid_id")
        except (ValueError, TypeError, json.JSONDecodeError):
            self.send_json(400, {"error": "invalid_payload"})
            return

        with connect_db() as db:
            db.execute("""
                INSERT INTO progress(profile_id,media_id,title,poster_url,position_ms,duration_ms,updated_at,completed)
                VALUES(?,?,?,?,?,?,?,?)
                ON CONFLICT(profile_id,media_id) DO UPDATE SET
                    title=excluded.title, poster_url=excluded.poster_url,
                    position_ms=excluded.position_ms, duration_ms=excluded.duration_ms,
                    updated_at=excluded.updated_at, completed=excluded.completed
                WHERE excluded.updated_at >= progress.updated_at
            """, (profile_id, media_id, title, poster_url, position_ms, duration_ms, updated_at, completed))
        self.send_json(200, {"status": "saved"})

    def do_OPTIONS(self):
        self.send_json(405, {"error": "method_not_allowed"})


if __name__ == "__main__":
    connect_db().close()
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
