#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NexusMount PC — interconexión sin Samba
- Exponer carpeta/disco (solo lectura) en Wi‑Fi / Tailscale
- Ver y copiar archivos de otro dispositivo NexusMount (Android o PC)
"""

from __future__ import annotations

import json
import os
import threading
import tkinter as tk
import urllib.parse
import urllib.request
from pathlib import Path
from tkinter import filedialog, messagebox, ttk
from typing import Optional

from nexus_share_server import DEFAULT_PORT, local_ipv4, start_server

CONFIG_DIR = Path.home() / ".nexusmount"
CONFIG_FILE = CONFIG_DIR / "pc_config.json"


def load_config() -> dict:
    try:
        if CONFIG_FILE.is_file():
            return json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
    except Exception:
        pass
    return {}


def save_config(data: dict) -> None:
    try:
        CONFIG_DIR.mkdir(parents=True, exist_ok=True)
        CONFIG_FILE.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
    except Exception as e:
        print("No se pudo guardar config:", e)



APP_TITLE = "NexusMount PC · Interconexión"


class NexusMountPC(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title(APP_TITLE)
        self.geometry("820x560")
        self.minsize(640, 420)
        self.configure(bg="#0b1326")

        self.cfg = load_config()
        self.server = None
        self.server_thread: Optional[threading.Thread] = None
        self.share_root = Path(self.cfg.get("share_root") or str(Path.home()))
        self.remote_host = str(self.cfg.get("last_remote_host") or "")
        self.remote_port = int(self.cfg.get("last_remote_port") or DEFAULT_PORT)
        self.auto_start = bool(self.cfg.get("auto_start_share", False))
        self.path_stack: list[str] = []

        self._build_ui()
        self._refresh_local_ips()
        if self.auto_start and self.share_root.is_dir():
            try:
                self.after(400, self._start_share)
            except Exception:
                pass

    def _build_ui(self) -> None:
        style = ttk.Style(self)
        try:
            style.theme_use("clam")
        except Exception:
            pass

        nb = ttk.Notebook(self)
        nb.pack(fill=tk.BOTH, expand=True, padx=8, pady=8)

        self.tab_share = ttk.Frame(nb)
        self.tab_view = ttk.Frame(nb)
        nb.add(self.tab_share, text="  Exponer mi disco  ")
        nb.add(self.tab_view, text="  Ver otro dispositivo  ")

        # --- Exponer ---
        f = self.tab_share
        ttk.Label(
            f,
            text="Expone una carpeta en la red (Wi‑Fi o Tailscale).\n"
            "Solo lectura: otros pueden ver y copiar, no borrar ni modificar.",
            wraplength=760,
        ).pack(anchor=tk.W, padx=12, pady=8)

        row = ttk.Frame(f)
        row.pack(fill=tk.X, padx=12, pady=4)
        ttk.Label(row, text="Carpeta:").pack(side=tk.LEFT)
        self.share_path_var = tk.StringVar(value=str(self.share_root))
        ttk.Entry(row, textvariable=self.share_path_var, width=60).pack(
            side=tk.LEFT, padx=6, fill=tk.X, expand=True
        )
        ttk.Button(row, text="Elegir…", command=self._pick_folder).pack(side=tk.LEFT)

        row2 = ttk.Frame(f)
        row2.pack(fill=tk.X, padx=12, pady=4)
        ttk.Label(row2, text="Puerto:").pack(side=tk.LEFT)
        self.port_var = tk.StringVar(value=str(self.cfg.get("share_port") or DEFAULT_PORT))
        ttk.Entry(row2, textvariable=self.port_var, width=8).pack(side=tk.LEFT, padx=6)

        self.status_var = tk.StringVar(value="Exposición: parada")
        ttk.Label(f, textvariable=self.status_var).pack(anchor=tk.W, padx=12, pady=4)

        self.ips_var = tk.StringVar(value="")
        ttk.Label(f, textvariable=self.ips_var, wraplength=760).pack(
            anchor=tk.W, padx=12, pady=4
        )

        btn_row = ttk.Frame(f)
        btn_row.pack(padx=12, pady=12)
        ttk.Button(btn_row, text="Iniciar exposición", command=self._start_share).pack(
            side=tk.LEFT, padx=4
        )
        ttk.Button(btn_row, text="Detener", command=self._stop_share).pack(
            side=tk.LEFT, padx=4
        )
        ttk.Button(
            btn_row, text="Actualizar IPs", command=self._refresh_local_ips
        ).pack(side=tk.LEFT, padx=4)

        ttk.Label(
            f,
            text="En el móvil: Interconexión → Conectar por IP → IP del PC + puerto.\n"
            "Tailscale: usa la IP 100.x del PC (app Tailscale → Machines).",
            wraplength=760,
        ).pack(anchor=tk.W, padx=12, pady=8)

        # --- Ver ---
        v = self.tab_view
        ttk.Label(
            v,
            text="Visor de disco remoto (Android o PC con exposición activa). Solo ver y copiar.",
            wraplength=760,
        ).pack(anchor=tk.W, padx=12, pady=8)

        crow = ttk.Frame(v)
        crow.pack(fill=tk.X, padx=12, pady=4)
        ttk.Label(crow, text="IP:").pack(side=tk.LEFT)
        self.remote_ip_var = tk.StringVar(value=str(self.cfg.get("last_remote_host") or ""))
        ttk.Entry(crow, textvariable=self.remote_ip_var, width=24).pack(
            side=tk.LEFT, padx=4
        )
        ttk.Label(crow, text="Puerto:").pack(side=tk.LEFT)
        self.remote_port_var = tk.StringVar(value=str(self.cfg.get("last_remote_port") or DEFAULT_PORT))
        ttk.Entry(crow, textvariable=self.remote_port_var, width=8).pack(
            side=tk.LEFT, padx=4
        )
        ttk.Button(crow, text="Conectar", command=self._connect_remote).pack(
            side=tk.LEFT, padx=6
        )
        ttk.Button(crow, text="↑ Subir", command=self._go_up).pack(side=tk.LEFT, padx=2)
        ttk.Button(crow, text="Raíz", command=self._go_root).pack(side=tk.LEFT, padx=2)

        self.path_var = tk.StringVar(value="/")
        ttk.Label(v, textvariable=self.path_var).pack(anchor=tk.W, padx=12)

        cols = ("name", "type", "size")
        self.tree = ttk.Treeview(v, columns=cols, show="headings", height=18)
        self.tree.heading("name", text="Nombre")
        self.tree.heading("type", text="Tipo")
        self.tree.heading("size", text="Tamaño")
        self.tree.column("name", width=420)
        self.tree.column("type", width=100)
        self.tree.column("size", width=100)
        self.tree.pack(fill=tk.BOTH, expand=True, padx=12, pady=8)
        self.tree.bind("<Double-1>", self._on_double)

        brow = ttk.Frame(v)
        brow.pack(fill=tk.X, padx=12, pady=4)
        ttk.Button(brow, text="Abrir / descargar", command=self._download_selected).pack(
            side=tk.LEFT, padx=4
        )
        ttk.Button(brow, text="Actualizar lista", command=self._refresh_remote).pack(
            side=tk.LEFT, padx=4
        )

        self.entries: list[dict] = []

    def _persist(self, **kwargs) -> None:
        self.cfg.update(kwargs)
        save_config(self.cfg)

    def _pick_folder(self) -> None:
        path = filedialog.askdirectory(initialdir=str(self.share_root))
        if path:
            self.share_root = Path(path)
            self.share_path_var.set(path)

    def _refresh_local_ips(self) -> None:
        ips = local_ipv4()
        self.ips_var.set(
            "IPs de este PC: " + (", ".join(ips) if ips else "(no detectadas)")
        )

    def _start_share(self) -> None:
        if self.server is not None:
            messagebox.showinfo(APP_TITLE, "Ya está expuesto. Detén primero si quieres cambiar.")
            return
        root = Path(self.share_path_var.get().strip() or str(Path.home()))
        if not root.is_dir():
            messagebox.showerror(APP_TITLE, f"No es una carpeta válida:\n{root}")
            return
        try:
            port = int(self.port_var.get().strip() or DEFAULT_PORT)
        except ValueError:
            messagebox.showerror(APP_TITLE, "Puerto inválido")
            return
        try:
            self.server = start_server(root, port)
        except Exception as e:
            messagebox.showerror(APP_TITLE, f"No se pudo iniciar:\n{e}")
            return

        def run() -> None:
            assert self.server is not None
            self.server.serve_forever()

        self.server_thread = threading.Thread(target=run, daemon=True)
        self.server_thread.start()
        self.share_root = root
        self._refresh_local_ips()
        ips = local_ipv4()
        self.status_var.set(f"Exposición ACTIVA · {root} · puerto {port}")
        self._persist(
            share_root=str(root),
            share_port=port,
            auto_start_share=True,
        )
        messagebox.showinfo(
            APP_TITLE,
            f"Disco expuesto (solo lectura)\n\nCarpeta: {root}\nPuerto: {port}\n"
            f"IPs: {', '.join(ips) or '—'}\n\n"
            "Configuración guardada en ~/.nexusmount/pc_config.json\n"
            "En Android: Interconexión → Conectar por IP.",
        )

    def _stop_share(self) -> None:
        if self.server is None:
            self.status_var.set("Exposición: parada")
            return
        try:
            self.server.shutdown()
        except Exception:
            pass
        self.server = None
        self.status_var.set("Exposición: parada")
        self._persist(auto_start_share=False)

    def _connect_remote(self) -> None:
        host = self.remote_ip_var.get().strip()
        if not host:
            messagebox.showwarning(APP_TITLE, "Introduce la IP")
            return
        try:
            port = int(self.remote_port_var.get().strip() or DEFAULT_PORT)
        except ValueError:
            messagebox.showerror(APP_TITLE, "Puerto inválido")
            return
        self.remote_host = host
        self.remote_port = port
        self.path_stack = []
        self._persist(last_remote_host=host, last_remote_port=port)
        hist = list(self.cfg.get("remote_history") or [])
        key = host if port == DEFAULT_PORT else f"{host}:{port}"
        if key in hist:
            hist.remove(key)
        hist.insert(0, key)
        self._persist(remote_history=hist[:20])
        self._refresh_remote()

    def _current_path(self) -> str:
        return "/".join(self.path_stack)

    def _refresh_remote(self) -> None:
        if not self.remote_host:
            return
        path = self._current_path()
        self.path_var.set("/" + path if path else "/")
        q = f"?path={urllib.parse.quote(path)}" if path else ""
        url = f"http://{self.remote_host}:{self.remote_port}/api/list{q}"
        try:
            with urllib.request.urlopen(url, timeout=10) as resp:
                data = json.loads(resp.read().decode("utf-8"))
        except Exception as e:
            messagebox.showerror(
                APP_TITLE,
                f"No se pudo conectar a {self.remote_host}:{self.remote_port}\n\n{e}\n\n"
                "¿El otro dispositivo tiene la exposición activa?",
            )
            return
        self.entries = data.get("entries") or []
        for i in self.tree.get_children():
            self.tree.delete(i)
        for e in self.entries:
            tipo = "Carpeta" if e.get("dir") else "Archivo"
            size = self._fmt(e.get("size") or 0) if not e.get("dir") else ""
            self.tree.insert("", tk.END, values=(e.get("name"), tipo, size))

    def _on_double(self, _evt=None) -> None:
        sel = self.tree.selection()
        if not sel:
            return
        idx = self.tree.index(sel[0])
        if idx < 0 or idx >= len(self.entries):
            return
        e = self.entries[idx]
        if e.get("dir"):
            self.path_stack.append(e["name"])
            self._refresh_remote()
        else:
            self._download_entry(e)

    def _go_up(self) -> None:
        if self.path_stack:
            self.path_stack.pop()
            self._refresh_remote()

    def _go_root(self) -> None:
        self.path_stack = []
        self._refresh_remote()

    def _download_selected(self) -> None:
        sel = self.tree.selection()
        if not sel:
            messagebox.showinfo(APP_TITLE, "Selecciona un archivo")
            return
        idx = self.tree.index(sel[0])
        e = self.entries[idx]
        if e.get("dir"):
            self.path_stack.append(e["name"])
            self._refresh_remote()
            return
        self._download_entry(e)

    def _download_entry(self, e: dict) -> None:
        rel = e.get("path") or e.get("name")
        dest_dir = filedialog.askdirectory(title="Guardar en carpeta…")
        if not dest_dir:
            return
        parts = [urllib.parse.quote(p) for p in rel.replace("\\", "/").split("/") if p]
        url = f"http://{self.remote_host}:{self.remote_port}/file/{'/'.join(parts)}"
        dest = Path(dest_dir) / e["name"]
        try:
            urllib.request.urlretrieve(url, dest)
            messagebox.showinfo(APP_TITLE, f"Guardado:\n{dest}")
            try:
                os.startfile(dest)  # Windows
            except Exception:
                try:
                    import subprocess
                    import sys

                    if sys.platform == "darwin":
                        subprocess.Popen(["open", str(dest)])
                    else:
                        subprocess.Popen(["xdg-open", str(dest)])
                except Exception:
                    pass
        except Exception as ex:
            messagebox.showerror(APP_TITLE, f"Error al descargar:\n{ex}")

    @staticmethod
    def _fmt(n: int) -> str:
        if n < 1024:
            return f"{n} B"
        if n < 1024 * 1024:
            return f"{n // 1024} KB"
        if n < 1024 * 1024 * 1024:
            return f"{n // (1024 * 1024)} MB"
        return f"{n // (1024 * 1024 * 1024)} GB"

    def on_close(self) -> None:
        try:
            self._persist(
                share_root=self.share_path_var.get().strip() or str(self.share_root),
                share_port=int(self.port_var.get() or DEFAULT_PORT),
                last_remote_host=self.remote_ip_var.get().strip(),
                last_remote_port=int(self.remote_port_var.get() or DEFAULT_PORT),
            )
        except Exception:
            pass
        # No detener exposición automáticamente: el usuario puede querer dejarla
        # Si prefiere detener al cerrar, descomenta: self._stop_share()
        self.destroy()


def main() -> None:
    app = NexusMountPC()
    app.protocol("WM_DELETE_WINDOW", app.on_close)
    app.mainloop()


if __name__ == "__main__":
    main()
