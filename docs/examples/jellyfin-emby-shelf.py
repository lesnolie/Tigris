#!/usr/bin/env python3
"""Serve a Tigris Top Shelf JSON feed from Jellyfin or Emby.

Tigris does not speak the Jellyfin or Emby API. It expects a JSON array:

    [{"title": "...", "meta": "...", "image": "http://..."}]

Run this on a computer the TV can reach. The API key stays in this process.
The TV only talks to the proxy.

    JELLYFIN_URL=http://192.168.1.10:8096 \\
    JELLYFIN_API_KEY=your-key \\
    python3 docs/examples/jellyfin-emby-shelf.py

Emby uses the same /Users and /Items routes and the X-Emby-Token header.
Point JELLYFIN_URL at whichever server you run.

In Tigris: Settings, Top Shelf, Custom source. Paste the printed /shelf address
and link the dock app that should show it.
"""

from __future__ import annotations

import json
import os
import sys
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LISTEN = os.environ.get("SHELF_LISTEN", "0.0.0.0")
PORT = int(os.environ.get("SHELF_PORT", "8766"))
UPSTREAM = os.environ.get("JELLYFIN_URL", "").rstrip("/")
TOKEN = os.environ.get("JELLYFIN_API_KEY", "")
LIMIT = 12
MAX_IMAGE_BYTES = 2 * 1024 * 1024


def api(path: str) -> object:
    request = urllib.request.Request(
        UPSTREAM + path,
        headers={"X-Emby-Token": TOKEN, "Accept": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=8) as response:
        return json.loads(response.read().decode("utf-8"))


def read_limited(response, limit: int) -> bytes:
    chunks = []
    total = 0
    while True:
        block = response.read(min(16384, limit - total + 1))
        if not block:
            return b"".join(chunks)
        total += len(block)
        if total > limit:
            raise ValueError("response too large")
        chunks.append(block)


def build_shelf(rows: object, public_base: str) -> list[dict]:
    """Map a Jellyfin/Emby Items array into the Tigris shelf array."""
    if not isinstance(rows, list):
        return []
    out = []
    for item in rows:
        if len(out) >= LIMIT:
            break
        if not isinstance(item, dict):
            continue
        title = str(item.get("Name") or "").strip()
        item_id = str(item.get("Id") or "").strip()
        tags = item.get("ImageTags") if isinstance(item.get("ImageTags"), dict) else {}
        if not title or not item_id or "/" in item_id or "\\" in item_id or not tags.get("Primary"):
            continue
        parts = [str(part) for part in (item.get("ProductionYear"), item.get("Type")) if part]
        overview = str(item.get("Overview") or "").strip().replace("\n", " ")
        if overview:
            parts.append(overview)
        out.append({
            "title": title[:120],
            "meta": " · ".join(parts)[:180],
            "image": public_base.rstrip("/") + "/image/" + urllib.parse.quote(item_id),
        })
    return out


def shelf_items(public_base: str) -> list[dict]:
    users = api("/Users")
    if not isinstance(users, list) or not users or not isinstance(users[0], dict):
        return []
    user_id = str(users[0].get("Id") or "")
    if not user_id:
        return []
    query = urllib.parse.urlencode({
        "Recursive": "true",
        "IncludeItemTypes": "Movie,Series",
        "SortBy": "DateCreated",
        "SortOrder": "Descending",
        "Limit": str(LIMIT),
        "Fields": "Overview,ProductionYear",
        "EnableImages": "true",
        "ImageTypeLimit": "1",
    })
    payload = api("/Users/" + urllib.parse.quote(user_id) + "/Items?" + query)
    rows = payload.get("Items") if isinstance(payload, dict) else None
    return build_shelf(rows, public_base)


class Handler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        path = urllib.parse.urlsplit(self.path).path
        if path == "/shelf":
            host = self.headers.get("Host") or f"127.0.0.1:{PORT}"
            try:
                body = json.dumps(shelf_items(f"http://{host}"), ensure_ascii=False).encode("utf-8")
            except Exception as exc:
                message = f"upstream failed: {exc}\n".encode("utf-8")
                self._send(502, "text/plain; charset=utf-8", message)
                return
            self._send(200, "application/json; charset=utf-8", body)
            return
        if path.startswith("/image/"):
            item_id = urllib.parse.unquote(path[len("/image/"):])
            if not item_id or "/" in item_id or "\\" in item_id:
                self.send_error(404)
                return
            upstream = (
                UPSTREAM
                + "/Items/"
                + urllib.parse.quote(item_id)
                + "/Images/Primary?maxWidth=1280&quality=80"
            )
            request = urllib.request.Request(upstream, headers={"X-Emby-Token": TOKEN})
            try:
                with urllib.request.urlopen(request, timeout=8) as response:
                    data = read_limited(response, MAX_IMAGE_BYTES)
                    content_type = response.headers.get("Content-Type", "image/jpeg").split(";", 1)[0]
            except Exception:
                self.send_error(502)
                return
            self._send(200, content_type, data)
            return
        self.send_error(404)

    def _send(self, status: int, content_type: str, body: bytes) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt: str, *args) -> None:
        sys.stderr.write("%s\n" % (fmt % args))


def main() -> None:
    if not UPSTREAM or not TOKEN:
        sys.exit("Set JELLYFIN_URL and JELLYFIN_API_KEY. See docs/top-shelf.md.")
    server = ThreadingHTTPServer((LISTEN, PORT), Handler)
    print(f"Paste this into Tigris: http://<this-computer-ip>:{PORT}/shelf")
    server.serve_forever()


if __name__ == "__main__":
    main()
