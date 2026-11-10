"""PengWa public card pages and JSON API. Run behind an HTTPS reverse proxy."""
import argparse
import base64
import hashlib
import hmac
import html
import json
import re
import sqlite3
import ssl
import unicodedata
from contextlib import closing
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

MAX_BODY = 192 * 1024
CARD_PATH = re.compile(r"(/c/|/api/cards/)([a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12})")
AUTH = re.compile(r"Bearer ([A-Za-z0-9_-]{43})")
INVISIBLE_FILLERS = "\u115f\u1160\u3164\uffa0\u2800"
CSS = Path(__file__).with_name("card.css").read_text(encoding="utf-8")


def image_type(value):
    if not value:
        return None
    if len(value) > 16_384:
        raise ValueError("image too large")
    data = base64.b64decode(value, validate=True)
    if data.startswith(b"\xff\xd8") and data.endswith(b"\xff\xd9"):
        return "jpeg"
    if len(data) >= 45 and data.startswith(b"\x89PNG\r\n\x1a\n") and data.endswith(b"\x00\x00\x00\x00IEND\xaeB\x60\x82"):
        return "png"
    raise ValueError("invalid image")


def valid_link(value):
    if any(c.isspace() or ord(c) < 32 or c in '\\<>"' for c in value):
        return False
    url = urlsplit(value)
    return url.scheme in ("http", "https") and url.hostname and url.username is None


def string(value, limit):
    if not isinstance(value, str) or len(value.encode("utf-16-le")) // 2 > limit:
        raise ValueError("invalid text")
    return value


def parse_card(data):
    raw = json.loads(data.decode("utf-8"))
    name = string(raw["displayName"], 32)
    if (
        not name or len(name.encode("utf-8")) > 32 or name != name.strip(" ")
        or name != unicodedata.normalize("NFC", name)
        or any(c != " " and (c in INVISIBLE_FILLERS or unicodedata.category(c) in
                             {"Cc", "Cf", "Co", "Cs", "Zl", "Zp", "Zs"}) for c in name)
    ):
        raise ValueError("invalid name")
    avatar = string(raw["avatar"], 16_384)
    image_type(avatar)
    blocks = raw["blocks"]
    if not isinstance(blocks, list) or len(blocks) > 12:
        raise ValueError("invalid blocks")
    clean = []
    for block in blocks:
        item = {key: string(block[key], limit) for key, limit in
                (("type", 6), ("title", 60), ("text", 1000), ("image", 16_384), ("url", 1000))}
        if item["type"] not in ("Text", "Link", "Social", "Image"):
            raise ValueError("invalid block")
        image_type(item["image"])
        if item["type"] == "Image" and not item["image"]:
            raise ValueError("missing image")
        if item["url"] and not valid_link(item["url"]):
            raise ValueError("invalid URL")
        if item["type"] == "Link" and not valid_link(item["text"]):
            raise ValueError("invalid link")
        clean.append(item)
    return {"displayName": name, "avatar": avatar, "blocks": clean}


def image(value, alt):
    return f'<img src="data:image/{image_type(value)};base64,{value}" alt="{html.escape(alt)}">'


def page(title, content):
    return f"""<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)} · 碰碰娃</title>
<link rel="stylesheet" href="/card.css">
</head>
<body>
<main>
<p class="brand">碰碰娃 · 在线名片</p>
{content}
</main>
</body>
</html>"""


def card_page(card):
    name = card["displayName"]
    avatar = image(card["avatar"], "") if card["avatar"] else f'<span class="initial" aria-hidden="true">{html.escape(name[0])}</span>'
    blocks = []
    for block in card["blocks"]:
        kind = block["type"]
        title = f'<h2>{html.escape(block["title"])}</h2>' if block["title"] else ""
        if kind == "Image":
            content = image(block["image"], block["title"] or "名片图片")
        else:
            content = f'<p>{html.escape(block["text"])}</p>' if kind != "Link" and block["text"] else ""
            url = block["text"] if kind == "Link" else block["url"]
            if url:
                label = "打开主页" if kind == "Social" else url
                content += f'<a href="{html.escape(url)}" rel="noreferrer">{html.escape(label)}</a>'
        blocks.append(f'<section>{title}{content}</section>')
    return page(name, f'<article><header>{avatar}<h1>{html.escape(name)}</h1></header>{"".join(blocks)}</article>')


class CardsHandler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(15)

    def log_message(self, format, *args):
        # The default access log includes public card URLs and IDs.
        pass

    def send(self, status, content_type=None, content=b""):
        if isinstance(content, str):
            content = content.encode("utf-8")
        self.send_response(status)
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Robots-Tag", "noindex, nofollow")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Content-Security-Policy",
                         "default-src 'none'; style-src 'self'; img-src data:; base-uri 'none'; frame-ancestors 'none'")
        if content_type:
            self.send_header("Content-Type", content_type)
        if status != 204:
            self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        if self.command != "HEAD" and content:
            self.wfile.write(content)

    def do_GET(self):
        if self.path == "/card.css":
            self.send(200, "text/css; charset=utf-8", CSS)
            return
        match = CARD_PATH.fullmatch(self.path)
        if not match:
            self.send(404)
            return
        prefix, card_id = match.groups()
        with closing(sqlite3.connect(self.server.database)) as db:
            row = db.execute("SELECT content FROM cards WHERE id = ?", (card_id,)).fetchone()
        if prefix == "/c/":
            if row and row[0] is not None:
                self.send(200, "text/html; charset=utf-8", card_page(json.loads(row[0])))
            else:
                self.send(404, "text/html; charset=utf-8",
                          page("名片不可用", "<h1>这张名片不可用</h1><p>链接可能已被持卡人停用。</p>"))
        elif row and row[0] is not None:
            self.send(200, "application/json; charset=utf-8", row[0])
        else:
            self.send(404)

    do_HEAD = do_GET

    def change_card(self, payload):
        match = CARD_PATH.fullmatch(self.path)
        if not match or match[1] != "/api/cards/":
            self.send(404)
            return
        secret = AUTH.fullmatch(self.headers.get("Authorization", ""))
        if not secret:
            self.send(401)
            return
        owner = hashlib.sha256(secret[1].encode("ascii")).digest()
        card_id = match[2]
        with closing(sqlite3.connect(self.server.database)) as db, db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT owner, content IS NULL FROM cards WHERE id = ?", (card_id,)).fetchone()
            if row and not hmac.compare_digest(row[0], owner):
                status = 403
            elif row and row[1] and payload is not None:
                status = 410
            else:
                db.execute(
                    "INSERT INTO cards VALUES (?, ?, ?) ON CONFLICT(id) DO UPDATE SET content = excluded.content",
                    (card_id, owner, payload),
                )
                status = 204
        self.send(status)

    def do_PUT(self):
        try:
            size = int(self.headers.get("Content-Length", ""))
        except ValueError:
            self.send(411)
            return
        if not 0 < size <= MAX_BODY:
            self.send(413)
            return
        if self.headers.get_content_type() != "application/json":
            self.send(415)
            return
        try:
            data = self.rfile.read(size)
            if len(data) != size:
                raise ValueError("incomplete card")
            payload = json.dumps(parse_card(data), ensure_ascii=False, separators=(",", ":"))
        except (KeyError, ValueError, TypeError, RecursionError, TimeoutError):
            self.send(400)
            return
        self.change_card(payload)

    def do_DELETE(self):
        self.change_card(None)


def create_server(bind, port, database):
    with closing(sqlite3.connect(database)) as db, db:
        db.execute("PRAGMA journal_mode = WAL")
        db.execute("CREATE TABLE IF NOT EXISTS cards (id TEXT PRIMARY KEY, owner BLOB NOT NULL, content TEXT)")
    server = ThreadingHTTPServer((bind, port), CardsHandler)
    server.database = database
    return server


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--db", type=Path, required=True)
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--port", default=8080, type=int)
    parser.add_argument("--cert", type=Path)
    parser.add_argument("--key", type=Path)
    args = parser.parse_args()
    if bool(args.cert) != bool(args.key):
        parser.error("--cert and --key must be used together")
    with create_server(args.bind, args.port, args.db) as server:
        if args.cert:
            tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            tls.load_cert_chain(args.cert, args.key)
            server.socket = tls.wrap_socket(server.socket, server_side=True)
        print(f"Card server listening on {args.bind}:{server.server_port}", flush=True)
        server.serve_forever()


if __name__ == "__main__":
    main()
