import json
import tempfile
import threading
import unittest
from http.client import HTTPConnection
from pathlib import Path

from cards import create_server


class OnlineCardsTest(unittest.TestCase):
    def test_publish_update_and_revoke(self):
        with tempfile.TemporaryDirectory() as tmp:
            database = Path(tmp) / "cards.sqlite3"
            with create_server("127.0.0.1", 0, database) as server:
                worker = threading.Thread(target=server.serve_forever, daemon=True)
                worker.start()
                self.addCleanup(worker.join, 2)
                try:
                    self.exercise_server(server.server_port)
                finally:
                    server.shutdown()

    def exercise_server(self, port):
        card_id = "ea05e2d2-cc60-4f8b-81c8-74b158eb2143"
        api, page = f"/api/cards/{card_id}", f"/c/{card_id}"
        owner = {"Authorization": f"Bearer {'A' * 43}", "Content-Type": "application/json"}
        stranger = {**owner, "Authorization": f"Bearer {'B' * 43}"}
        card = {"displayName": "小棉花", "avatar": "", "blocks": [
            {"type": "Text", "title": "介绍", "text": "<script>bad()</script>", "image": "", "url": ""},
            {"type": "Link", "title": "", "text": "https://example.com?a=1&b=2", "image": "", "url": ""},
        ]}

        def request(method, path, headers=None, body=None):
            conn = HTTPConnection("127.0.0.1", port, timeout=2)
            try:
                conn.request(method, path, None if body is None else json.dumps(body), headers or {})
                response = conn.getresponse()
                return response.status, response.read()
            finally:
                conn.close()

        self.assertEqual(request("GET", api)[0], 404)
        self.assertEqual(request("PUT", api, body=card)[0], 415)
        self.assertEqual(request("PUT", api, {"Content-Type": "application/json"}, card)[0], 401)
        self.assertEqual(request("PUT", api, owner, card)[0], 204)
        self.assertEqual(json.loads(request("GET", api)[1]), card)
        status, body = request("GET", page)
        self.assertEqual(status, 200)
        self.assertIn(b"&lt;script&gt;", body)
        self.assertIn(b"&amp;b=2", body)
        self.assertEqual(request("PUT", api, stranger, card)[0], 403)
        self.assertEqual(request("DELETE", api, stranger)[0], 403)
        card["displayName"] = "新名字"
        self.assertEqual(request("PUT", api, owner, card)[0], 204)
        self.assertIn("新名字".encode(), request("GET", page)[1])
        card["blocks"][1]["text"] = "javascript:alert(1)"
        self.assertEqual(request("PUT", api, owner, card)[0], 400)
        self.assertEqual(request("DELETE", api, owner)[0], 204)
        self.assertEqual(request("DELETE", api, owner)[0], 204)
        self.assertEqual(request("GET", api)[0], 404)
        self.assertEqual(request("GET", page)[0], 404)
        card["blocks"].pop()
        self.assertEqual(request("PUT", api, owner, card)[0], 410)
