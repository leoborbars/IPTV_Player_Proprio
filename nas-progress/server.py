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
LIST_RE = re.compile(r"^(?:leandro|[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})$")

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
    connection.execute("""
        CREATE TABLE IF NOT EXISTS playback_preferences (
            profile_id TEXT PRIMARY KEY,
            next_prompt_seconds INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
    """)
    connection.execute("""
        CREATE TABLE IF NOT EXISTS media_lists (
            profile_id TEXT NOT NULL, list_id TEXT NOT NULL, name TEXT NOT NULL,
            updated_at INTEGER NOT NULL, active INTEGER NOT NULL DEFAULT 1,
            PRIMARY KEY (profile_id, list_id)
        )
    """)
    connection.execute("""
        CREATE TABLE IF NOT EXISTS media_list_items (
            profile_id TEXT NOT NULL, list_id TEXT NOT NULL, media_type TEXT NOT NULL,
            media_id INTEGER NOT NULL, title TEXT NOT NULL, poster_url TEXT,
            updated_at INTEGER NOT NULL, active INTEGER NOT NULL DEFAULT 1,
            series_id INTEGER,
            PRIMARY KEY (profile_id, list_id, media_type, media_id)
        )
    """)
    if "series_id" not in {row[1] for row in connection.execute("PRAGMA table_info(media_list_items)")}:
        connection.execute("ALTER TABLE media_list_items ADD COLUMN series_id INTEGER")
    connection.execute("CREATE TABLE IF NOT EXISTS schema_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    migrated = connection.execute("SELECT 1 FROM schema_meta WHERE key='favorites_to_leandro'").fetchone()
    if not migrated:
        connection.execute("""
            INSERT OR IGNORE INTO media_lists(profile_id,list_id,name,updated_at,active)
            SELECT profile_id,'leandro','Leandro',1,1 FROM favorites GROUP BY profile_id
        """)
        connection.execute("""
            INSERT OR IGNORE INTO media_list_items
                (profile_id,list_id,media_type,media_id,title,poster_url,updated_at,active)
            SELECT profile_id,'leandro',media_type,media_id,title,poster_url,updated_at,active
            FROM favorites
        """)
        connection.execute("INSERT INTO schema_meta(key,value) VALUES('favorites_to_leandro','1')")
    connection.commit()
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
        if parsed.path not in ("/api/v1/progress", "/api/v1/favorites", "/api/v1/playback-preferences", "/api/v1/lists"):
            self.send_json(404, {"error": "not_found"})
            return
        if not self.authorized():
            self.send_json(401, {"error": "unauthorized"})
            return
        profile_id = parse_qs(parsed.query).get("profile_id", [""])[0]
        if not PROFILE_RE.fullmatch(profile_id):
            self.send_json(400, {"error": "invalid_profile_id"})
            return
        if parsed.path == "/api/v1/playback-preferences":
            with closing(connect_db()) as db:
                row = db.execute(
                    "SELECT next_prompt_seconds,updated_at FROM playback_preferences WHERE profile_id=?",
                    (profile_id,),
                ).fetchone()
            self.send_json(200, dict(row) if row else {})
            return
        if parsed.path == "/api/v1/lists":
            with closing(connect_db()) as db:
                lists = db.execute(
                    "SELECT list_id,name,updated_at,active FROM media_lists WHERE profile_id=? ORDER BY name LIMIT 500",
                    (profile_id,),
                ).fetchall()
                items = db.execute(
                    "SELECT list_id,media_type,media_id,title,poster_url,updated_at,active,series_id "
                    "FROM media_list_items WHERE profile_id=? ORDER BY updated_at DESC LIMIT 20000",
                    (profile_id,),
                ).fetchall()
            self.send_json(200, {"lists": [dict(row) for row in lists],
                                 "items": [dict(row) for row in items]})
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
        if path not in ("/api/v1/progress", "/api/v1/favorites", "/api/v1/playback-preferences",
                        "/api/v1/lists", "/api/v1/list-items"):
            self.send_json(404, {"error": "not_found"})
            return
        if not self.authorized():
            self.send_json(401, {"error": "unauthorized"})
            return
        if path == "/api/v1/playback-preferences":
            self.put_playback_preferences()
            return
        if path in ("/api/v1/lists", "/api/v1/list-items"):
            self.put_media_list(path)
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
                db.execute("""
                    INSERT INTO media_lists(profile_id,list_id,name,updated_at,active)
                    VALUES(?,'leandro','Leandro',1,1)
                    ON CONFLICT(profile_id,list_id) DO NOTHING
                """, (profile_id,))
                db.execute("""
                    INSERT INTO media_list_items(profile_id,list_id,media_type,media_id,title,poster_url,updated_at,active)
                    VALUES(?,'leandro',?,?,?,?,?,?)
                    ON CONFLICT(profile_id,list_id,media_type,media_id) DO UPDATE SET
                        title=excluded.title, poster_url=excluded.poster_url,
                        updated_at=excluded.updated_at, active=excluded.active
                    WHERE excluded.updated_at > media_list_items.updated_at
                """, (profile_id, media_type, media_id, title, poster_url, updated_at, active))
        self.send_json(200, {"status": "saved"})

    def put_media_list(self, path):
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 16384:
                raise ValueError("invalid_body_size")
            item = json.loads(self.rfile.read(length))
            profile_id = str(item.get("profile_id", ""))
            list_id = str(item.get("list_id", ""))
            updated_at = int(item.get("updated_at", 0))
            active = 1 if bool(item.get("active", False)) else 0
            if not PROFILE_RE.fullmatch(profile_id) or not LIST_RE.fullmatch(list_id):
                raise ValueError("invalid_id")
            if not 0 < updated_at <= int(time.time() * 1000) + 300000:
                raise ValueError("invalid_timestamp")
            if path == "/api/v1/lists":
                name = str(item.get("name", "")).strip()
                if not name or len(name) > 80 or (list_id == "leandro" and name != "Leandro"):
                    raise ValueError("invalid_list")
            else:
                media_type = str(item.get("media_type", ""))
                media_id = int(item.get("media_id", 0))
                title = str(item.get("title", "")).strip()[:500] or "Sem título"
                poster_url = item.get("poster_url")
                if poster_url is not None:
                    poster_url = str(poster_url)[:2048]
                    if not poster_url.startswith(("http://", "https://")):
                        poster_url = None
                if media_type not in ("movie", "series", "episode") or not 0 < media_id <= 2**31 - 1:
                    raise ValueError("invalid_media")
                series_id = item.get("series_id")
                if series_id is not None:
                    series_id = int(series_id)
                    if not 0 < series_id <= 2**31 - 1:
                        raise ValueError("invalid_series_id")
                if media_type == "episode" and series_id is None:
                    raise ValueError("missing_series_id")
        except (ValueError, TypeError, AttributeError, json.JSONDecodeError):
            self.send_json(400, {"error": "invalid_payload"})
            return
        with closing(connect_db()) as db, db:
            if path == "/api/v1/lists":
                db.execute("""
                    INSERT INTO media_lists(profile_id,list_id,name,updated_at,active)
                    VALUES(?,?,?,?,?)
                    ON CONFLICT(profile_id,list_id) DO UPDATE SET
                        name=excluded.name, updated_at=excluded.updated_at, active=excluded.active
                    WHERE excluded.updated_at >= media_lists.updated_at
                """, (profile_id, list_id, name, updated_at, active))
            else:
                db.execute("""
                    INSERT INTO media_list_items(profile_id,list_id,media_type,media_id,title,poster_url,updated_at,active,series_id)
                    VALUES(?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(profile_id,list_id,media_type,media_id) DO UPDATE SET
                        title=excluded.title, poster_url=excluded.poster_url,
                        updated_at=excluded.updated_at, active=excluded.active,
                        series_id=excluded.series_id
                    WHERE excluded.updated_at >= media_list_items.updated_at
                """, (profile_id, list_id, media_type, media_id, title, poster_url, updated_at, active, series_id))
                if list_id == "leandro" and media_type in ("movie", "series"):
                    db.execute("""
                        INSERT INTO favorites(profile_id,media_type,media_id,title,poster_url,updated_at,active)
                        VALUES(?,?,?,?,?,?,?)
                        ON CONFLICT(profile_id,media_type,media_id) DO UPDATE SET
                            title=excluded.title, poster_url=excluded.poster_url,
                            updated_at=excluded.updated_at, active=excluded.active
                        WHERE excluded.updated_at >= favorites.updated_at
                    """, (profile_id, media_type, media_id, title, poster_url, updated_at, active))
        self.send_json(200, {"status": "saved"})

    def put_playback_preferences(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 4096:
                raise ValueError("invalid_body_size")
            item = json.loads(self.rfile.read(length))
            if not isinstance(item, dict):
                raise ValueError("invalid_payload")
            profile_id = str(item.get("profile_id", ""))
            seconds = int(item.get("next_prompt_seconds"))
            updated_at = int(item.get("updated_at"))
            if not PROFILE_RE.fullmatch(profile_id) or not 5 <= seconds <= 300:
                raise ValueError("invalid_preferences")
            if not 0 < updated_at <= int(time.time() * 1000) + 300000:
                raise ValueError("invalid_timestamp")
        except (ValueError, TypeError, json.JSONDecodeError):
            self.send_json(400, {"error": "invalid_payload"})
            return
        with closing(connect_db()) as db, db:
            db.execute("""
                INSERT INTO playback_preferences(profile_id,next_prompt_seconds,updated_at)
                VALUES(?,?,?)
                ON CONFLICT(profile_id) DO UPDATE SET
                    next_prompt_seconds=excluded.next_prompt_seconds,
                    updated_at=excluded.updated_at
                WHERE excluded.updated_at >= playback_preferences.updated_at
            """, (profile_id, seconds, updated_at))
        self.send_json(200, {"status": "saved"})

    def do_OPTIONS(self):
        self.send_json(405, {"error": "method_not_allowed"})


if __name__ == "__main__":
    connect_db().close()
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
