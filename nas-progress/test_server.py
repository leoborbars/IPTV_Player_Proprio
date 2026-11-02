"""Regression checks for NAS sync endpoints and migrations."""
import json
import os
import sqlite3
import tempfile
import threading
import time
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

os.environ.setdefault("PROGRESS_SYNC_TOKEN", "test-token-" * 4)
import server


class PlaybackPreferencesApiTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        server.DB_PATH = os.path.join(self.directory.name, "progress.sqlite3")
        self.http = server.ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        self.thread = threading.Thread(target=self.http.serve_forever, daemon=True)
        self.thread.start()
        self.base = f"http://127.0.0.1:{self.http.server_port}"
        self.profile = "a" * 64

    def tearDown(self):
        self.http.shutdown()
        self.http.server_close()
        self.thread.join(timeout=2)
        self.directory.cleanup()

    def request(self, method, path, payload=None, authorized=True):
        headers = {"Content-Type": "application/json"}
        if authorized:
            headers["Authorization"] = "Bearer " + server.TOKEN
        body = json.dumps(payload).encode() if payload is not None else None
        with urlopen(Request(self.base + path, data=body, headers=headers, method=method), timeout=3) as response:
            return json.load(response)

    def test_preference_is_shared_and_older_updates_do_not_replace_it(self):
        path = f"/api/v1/playback-preferences?profile_id={self.profile}"
        self.assertEqual({}, self.request("GET", path))
        now = int(time.time() * 1000)
        self.request("PUT", "/api/v1/playback-preferences", {
            "profile_id": self.profile, "next_prompt_seconds": 15, "updated_at": now
        })
        self.request("PUT", "/api/v1/playback-preferences", {
            "profile_id": self.profile, "next_prompt_seconds": 5, "updated_at": now - 1
        })
        self.assertEqual({"next_prompt_seconds": 15, "updated_at": now}, self.request("GET", path))

    def test_invalid_value_and_missing_token_are_rejected(self):
        payload = {"profile_id": self.profile, "next_prompt_seconds": 301,
                   "updated_at": int(time.time() * 1000)}
        with self.assertRaises(HTTPError) as invalid:
            self.request("PUT", "/api/v1/playback-preferences", payload)
        self.assertEqual(400, invalid.exception.code)
        with self.assertRaises(HTTPError) as unauthorized:
            self.request("GET", f"/api/v1/playback-preferences?profile_id={self.profile}",
                         authorized=False)
        self.assertEqual(401, unauthorized.exception.code)

    def test_existing_favorites_migrate_to_leandro_and_list_updates_sync(self):
        now = int(time.time() * 1000)
        self.request("PUT", "/api/v1/favorites", {
            "profile_id": self.profile, "media_type": "movie", "media_id": 7,
            "title": "Filme antigo", "poster_url": None, "updated_at": now, "active": True,
        })
        path = f"/api/v1/lists?profile_id={self.profile}"
        first = self.request("GET", path)
        self.assertEqual("Leandro", first["lists"][0]["name"])
        self.assertEqual(7, first["items"][0]["media_id"])
        self.request("PUT", "/api/v1/lists", {
            "profile_id": self.profile, "list_id": "11111111-1111-1111-1111-111111111111",
            "name": "Teste", "updated_at": now, "active": True,
        })
        self.request("PUT", "/api/v1/list-items", {
            "profile_id": self.profile, "list_id": "11111111-1111-1111-1111-111111111111",
            "media_type": "episode", "media_id": 15, "series_id": 3,
            "title": "Episódio", "poster_url": None, "updated_at": now, "active": True,
        })
        updated = self.request("GET", path)
        self.assertEqual(2, len(updated["lists"]))
        self.assertEqual(3, next(i for i in updated["items"] if i["media_type"] == "episode")["series_id"])
        self.request("PUT", "/api/v1/list-items", {
            "profile_id": self.profile, "list_id": "leandro", "media_type": "movie",
            "media_id": 7, "title": "Filme antigo", "poster_url": None,
            "updated_at": now + 1, "active": False,
        })
        self.request("PUT", "/api/v1/list-items", {
            "profile_id": self.profile, "list_id": "leandro", "media_type": "movie",
            "media_id": 7, "title": "Filme antigo", "poster_url": None,
            "updated_at": now, "active": True,
        })
        self.assertFalse(self.request("GET", f"/api/v1/favorites?profile_id={self.profile}")["items"][0]["active"])

    def test_migration_from_preexisting_favorites_table(self):
        now = int(time.time() * 1000)
        with sqlite3.connect(server.DB_PATH) as db:
            db.execute("""CREATE TABLE favorites (
                profile_id TEXT, media_type TEXT, media_id INTEGER, title TEXT,
                poster_url TEXT, updated_at INTEGER, active INTEGER,
                PRIMARY KEY(profile_id, media_type, media_id))""")
            db.execute("INSERT INTO favorites VALUES (?,?,?,?,?,?,?)",
                       (self.profile, "series", 9, "Série antiga", None, now, 1))
        result = self.request("GET", f"/api/v1/lists?profile_id={self.profile}")
        self.assertEqual("Leandro", result["lists"][0]["name"])
        self.assertEqual(9, result["items"][0]["media_id"])

    def test_leandro_can_be_removed_without_reimporting_old_favorites(self):
        now = int(time.time() * 1000)
        self.request("PUT", "/api/v1/favorites", {
            "profile_id": self.profile, "media_type": "movie", "media_id": 7,
            "title": "Filme antigo", "poster_url": None, "updated_at": now - 3,
            "active": True,
        })
        path = f"/api/v1/lists?profile_id={self.profile}"
        self.request("GET", path)
        self.request("PUT", "/api/v1/lists", {
            "profile_id": self.profile, "list_id": "leandro", "name": "Leandro",
            "updated_at": now, "active": False,
        })
        self.request("PUT", "/api/v1/lists", {
            "profile_id": self.profile, "list_id": "leandro", "name": "Leandro",
            "updated_at": now - 1, "active": True,
        })
        result = self.request("GET", path)
        self.assertFalse(next(item for item in result["lists"] if item["list_id"] == "leandro")["active"])
        self.assertEqual(7, result["items"][0]["media_id"])


if __name__ == "__main__":
    unittest.main()
