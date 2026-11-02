"""Regression checks for the profile-scoped playback preference endpoint."""
import json
import os
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


if __name__ == "__main__":
    unittest.main()
