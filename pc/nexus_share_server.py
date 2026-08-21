#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Servidor HTTP solo lectura — compatible con NexusMount Android (puerto 8765)."""

from __future__ import annotations

import json
import mimetypes
import os
import socket
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Optional

DEFAULT_PORT = 8765


def local_ipv4() -> list[str]:
    ips: list[str] = []
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ip = info[4][0]
            if not ip.startswith("127."):
                ips.append(ip)
    except Exception:
        pass
    # Método alternativo
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ips.append(s.getsockname()[0])
        s.close()
    except Exception:
        pass
    # Interfaces
    try:
        import netifaces  # optional
        for iface in netifaces.interfaces():
            addrs = netifaces.ifaddresses(iface).get(netifaces.AF_INET, [])
            for a in addrs:
                ip = a.get("addr", "")
                if ip and not ip.startswith("127."):
                    ips.append(ip)
    except Exception:
        pass
    return sorted(set(ips))


class ReadOnlyHandler(BaseHTTPRequestHandler):
    root: Path = Path.home()

    def log_message(self, fmt: str, *args) -> None:
        print(f"[share] {args[0] if args else fmt}")

    def _safe(self, rel: str) -> Optional[Path]:
        rel = urllib.parse.unquote(rel).replace("\\", "/").lstrip("/")
        if ".." in rel.split("/"):
            return None
        target = (self.root / rel).resolve()
        try:
            target.relative_to(self.root.resolve())
        except ValueError:
            return None
        return target

    def do_GET(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        qs = urllib.parse.parse_qs(parsed.query)

        if path in ("/", "/index.html"):
            body = self._html().encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        if path.startswith("/api/info"):
            data = {
                "app": "NexusMount",
                "mode": "read-only",
                "root": str(self.root),
                "port": DEFAULT_PORT,
                "ips": local_ipv4(),
                "platform": "pc",
            }
            self._json(data)
            return

        if path.startswith("/api/list"):
            rel = (qs.get("path") or [""])[0]
            target = self._safe(rel)
            if target is None:
                self.send_error(403, "Ruta no permitida")
                return
            if not target.exists():
                self.send_error(404, "No existe")
                return
            if not target.is_dir():
                self.send_error(400, "No es carpeta")
                return
            entries = []
            try:
                children = sorted(target.iterdir(), key=lambda p: (not p.is_dir(), p.name.lower()))
            except PermissionError:
                self.send_error(403, "Sin permiso")
                return
            for child in children:
                name = child.name
                child_rel = name if not rel else f"{rel.strip('/')}/{name}"
                entries.append(
                    {
                        "name": name,
                        "dir": child.is_dir(),
                        "size": child.stat().st_size if child.is_file() else 0,
                        "path": child_rel,
                    }
                )
            self._json({"root": str(self.root), "path": rel, "entries": entries})
            return

        if path.startswith("/file/"):
            rel = path[len("/file/") :]
            rel = urllib.parse.unquote(rel)
            target = self._safe(rel)
            if target is None or not target.is_file():
                self.send_error(404, "Archivo no encontrado")
                return
            try:
                data = target.read_bytes()
            except Exception as e:
                self.send_error(500, str(e))
                return
            mime, _ = mimetypes.guess_type(str(target))
            self.send_response(200)
            self.send_header("Content-Type", mime or "application/octet-stream")
            self.send_header("Content-Length", str(len(data)))
            self.send_header(
                "Content-Disposition", f'inline; filename="{target.name}"'
            )
            self.end_headers()
            self.wfile.write(data)
            return

        self.send_error(404)

    def do_PUT(self) -> None:
        self.send_error(405, "Solo lectura")

    def do_POST(self) -> None:
        self.send_error(405, "Solo lectura")

    def do_DELETE(self) -> None:
        self.send_error(405, "Solo lectura")

    def _json(self, obj: dict) -> None:
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def _html(self) -> str:
        ips = ", ".join(local_ipv4()) or "(sin IP)"
        return f"""<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>NexusMount PC</title></head>
<body style="font-family:system-ui;background:#0b1326;color:#dae2fd;padding:2rem">
<h1>NexusMount PC · solo lectura</h1>
<p>Raíz: <code>{self.root}</code></p>
<p>IPs: {ips} · puerto {DEFAULT_PORT}</p>
<p>Compatible con la app Android (Interconexión).</p>
<p>API: /api/list?path= · /file/... · /api/info</p>
</body></html>"""


def start_server(root: Path, port: int = DEFAULT_PORT) -> ThreadingHTTPServer:
    root = root.resolve()
    if not root.is_dir():
        raise NotADirectoryError(str(root))
    ReadOnlyHandler.root = root
    server = ThreadingHTTPServer(("0.0.0.0", port), ReadOnlyHandler)
    return server
