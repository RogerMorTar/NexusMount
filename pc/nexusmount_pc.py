#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NexusMount PC — réplica de escritorio de la app Android.
Mismas áreas principales: Dashboard, Unidades, Explorador, Transferencias,
Backups, Interconexión, Colaborativo, Memoria, Limpieza, Rigo, Ajustes.
"""

from __future__ import annotations

import json
import os
import platform
import shutil
import socket
import subprocess
import sys
import threading
import tkinter as tk
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path
from tkinter import filedialog, messagebox, simpledialog, ttk
from typing import Optional

from nexus_share_server import DEFAULT_PORT, local_ipv4, start_server

APP_TITLE = "NexusMount PC"
CONFIG_DIR = Path.home() / ".nexusmount"
CONFIG_FILE = CONFIG_DIR / "pc_config.json"
HISTORY_FILE = CONFIG_DIR / "transfers.json"
SPACES_FILE = CONFIG_DIR / "collab_spaces.json"
BG = "#0b1326"
FG = "#dae2fd"
ACCENT = "#2dd4bf"
MUTED = "#8c909f"


def load_json(path: Path, default):
    try:
        if path.is_file():
            return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        pass
    return default


def save_json(path: Path, data) -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")


def load_config() -> dict:
    return load_json(CONFIG_FILE, {})


def save_config(data: dict) -> None:
    save_json(CONFIG_FILE, data)


def fmt_size(n: int) -> str:
    if n < 1024:
        return f"{n} B"
    if n < 1024 * 1024:
        return f"{n / 1024:.1f} KB"
    if n < 1024 * 1024 * 1024:
        return f"{n / (1024 * 1024):.1f} MB"
    return f"{n / (1024 ** 3):.2f} GB"


def open_path(path: Path) -> None:
    try:
        if sys.platform == "win32":
            os.startfile(path)  # type: ignore
        elif sys.platform == "darwin":
            subprocess.Popen(["open", str(path)])
        else:
            subprocess.Popen(["xdg-open", str(path)])
    except Exception as e:
        messagebox.showerror(APP_TITLE, str(e))


class NexusMountPC(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title(f"{APP_TITLE} · réplica escritorio")
        self.geometry("980x640")
        self.minsize(800, 520)
        self.configure(bg=BG)

        self.cfg = load_config()
        self.server = None
        self.server_thread: Optional[threading.Thread] = None
        self.share_root = Path(self.cfg.get("share_root") or str(Path.home()))
        self.explorer_path = Path(self.cfg.get("explorer_path") or str(Path.home()))
        self.clipboard_paths: list[Path] = []
        self.clipboard_cut = False
        self.remote_host = str(self.cfg.get("last_remote_host") or "")
        self.remote_port = int(self.cfg.get("last_remote_port") or DEFAULT_PORT)
        self.path_stack: list[str] = []
        self.remote_entries: list[dict] = []
        self.drives = list(self.cfg.get("drives") or [])
        self.auto_start = bool(self.cfg.get("auto_start_share", False))

        self._style()
        self._build()
        if self.auto_start and self.share_root.is_dir():
            self.after(500, self._start_share_silent)

    def _style(self) -> None:
        style = ttk.Style(self)
        try:
            style.theme_use("clam")
        except Exception:
            pass
        style.configure("TNotebook", background=BG)
        style.configure("TFrame", background=BG)
        style.configure("TLabel", background=BG, foreground=FG)
        style.configure("TButton", padding=4)

    def _persist(self, **kw) -> None:
        self.cfg.update(kw)
        save_config(self.cfg)

    def _build(self) -> None:
        top = ttk.Frame(self)
        top.pack(fill=tk.X, padx=8, pady=6)
        ttk.Label(top, text="NexusMount PC", font=("", 14, "bold")).pack(side=tk.LEFT)
        ttk.Label(top, text="Réplica de la app Android · escritorio", foreground=ACCENT).pack(
            side=tk.LEFT, padx=12
        )

        self.nb = ttk.Notebook(self)
        self.nb.pack(fill=tk.BOTH, expand=True, padx=8, pady=4)

        self.tab_dash = ttk.Frame(self.nb)
        self.tab_drives = ttk.Frame(self.nb)
        self.tab_files = ttk.Frame(self.nb)
        self.tab_xfer = ttk.Frame(self.nb)
        self.tab_backup = ttk.Frame(self.nb)
        self.tab_inter = ttk.Frame(self.nb)
        self.tab_collab = ttk.Frame(self.nb)
        self.tab_mem = ttk.Frame(self.nb)
        self.tab_clean = ttk.Frame(self.nb)
        self.tab_rigo = ttk.Frame(self.nb)
        self.tab_settings = ttk.Frame(self.nb)

        for tab, name in [
            (self.tab_dash, "Dashboard"),
            (self.tab_drives, "Unidades"),
            (self.tab_files, "Explorador"),
            (self.tab_xfer, "Transferencias"),
            (self.tab_backup, "Backups"),
            (self.tab_inter, "Interconexión"),
            (self.tab_collab, "Colaborativo"),
            (self.tab_mem, "Memoria"),
            (self.tab_clean, "Limpieza"),
            (self.tab_rigo, "Rigo IA"),
            (self.tab_settings, "Ajustes"),
        ]:
            self.nb.add(tab, text=f"  {name}  ")

        self._build_dashboard()
        self._build_drives()
        self._build_explorer()
        self._build_transfers()
        self._build_backup()
        self._build_interconnect()
        self._build_collab()
        self._build_memory()
        self._build_cleanup()
        self._build_rigo()
        self._build_settings()

        self.protocol("WM_DELETE_WINDOW", self.on_close)

    # ─── Dashboard ───────────────────────────────────────────
    def _build_dashboard(self) -> None:
        f = self.tab_dash
        self.dash_text = tk.Text(f, height=24, bg="#131b2e", fg=FG, insertbackground=FG, wrap=tk.WORD)
        self.dash_text.pack(fill=tk.BOTH, expand=True, padx=8, pady=8)
        ttk.Button(f, text="Actualizar", command=self._refresh_dashboard).pack(pady=4)
        self._refresh_dashboard()

    def _refresh_dashboard(self) -> None:
        ips = local_ipv4()
        share = "ACTIVA" if self.server else "parada"
        lines = [
            f"NexusMount PC — {platform.system()} {platform.machine()}",
            f"Hora: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
            "",
            f"Interconexión (exposición): {share}",
            f"Carpeta expuesta: {self.share_root}",
            f"Puerto: {self.cfg.get('share_port', DEFAULT_PORT)}",
            f"IPs locales: {', '.join(ips) or '—'}",
            "",
            f"Unidades guardadas: {len(self.drives)}",
            f"Última IP remota: {self.remote_host or '—'}:{self.remote_port}",
            f"Explorador en: {self.explorer_path}",
            "",
            "Módulos (como en Android):",
            "  Dashboard · Unidades · Explorador · Transferencias · Backups",
            "  Interconexión · Colaborativo · Memoria · Limpieza · Rigo · Ajustes",
            "",
            "Tip: Interconexión = solo lectura. SMB/edición en Unidades si configuras rutas.",
        ]
        self.dash_text.delete("1.0", tk.END)
        self.dash_text.insert(tk.END, "\n".join(lines))

    # ─── Unidades ────────────────────────────────────────────
    def _build_drives(self) -> None:
        f = self.tab_drives
        ttk.Label(
            f,
            text="Unidades y accesos rápidos (locales + historial SMB/interconexión)",
        ).pack(anchor=tk.W, padx=8, pady=6)
        row = ttk.Frame(f)
        row.pack(fill=tk.X, padx=8)
        ttk.Button(row, text="Añadir carpeta local", command=self._add_local_drive).pack(
            side=tk.LEFT, padx=2
        )
        ttk.Button(row, text="Añadir SMB (ruta)", command=self._add_smb_drive).pack(
            side=tk.LEFT, padx=2
        )
        ttk.Button(row, text="Abrir seleccionada", command=self._open_drive).pack(
            side=tk.LEFT, padx=2
        )
        ttk.Button(row, text="Quitar", command=self._remove_drive).pack(side=tk.LEFT, padx=2)
        ttk.Button(row, text="Actualizar", command=self._reload_drives).pack(side=tk.LEFT, padx=2)

        self.drives_list = tk.Listbox(f, bg="#131b2e", fg=FG, height=20)
        self.drives_list.pack(fill=tk.BOTH, expand=True, padx=8, pady=8)
        self.drives_list.bind("<Double-1>", lambda e: self._open_drive())
        self._reload_drives()

    def _reload_drives(self) -> None:
        self.drives_list.delete(0, tk.END)
        # Always show home + roots
        defaults = [
            {"name": "Inicio usuario", "type": "LOCAL", "path": str(Path.home())},
            {"name": "Almacenamiento", "type": "LOCAL", "path": str(Path.home().anchor or "/")},
        ]
        if sys.platform == "win32":
            for letter in "CDEFGH":
                p = f"{letter}:\\"
                if Path(p).exists():
                    defaults.append({"name": f"Disco {letter}:", "type": "LOCAL", "path": p})
        shown = defaults + self.drives
        self._drives_shown = shown
        for d in shown:
            self.drives_list.insert(
                tk.END, f"[{d.get('type','?')}] {d.get('name')}  —  {d.get('path')}"
            )

    def _add_local_drive(self) -> None:
        p = filedialog.askdirectory()
        if not p:
            return
        self.drives.append({"name": Path(p).name or p, "type": "LOCAL", "path": p})
        self._persist(drives=self.drives)
        self._reload_drives()

    def _add_smb_drive(self) -> None:
        path = simpledialog.askstring(
            APP_TITLE,
            "Ruta SMB o UNC (ej. //192.168.1.10/share o \\\\server\\share)\n"
            "En Linux también puedes montar antes y añadir la carpeta local.",
        )
        if not path:
            return
        name = simpledialog.askstring(APP_TITLE, "Nombre visible:", initialvalue=path) or path
        self.drives.append({"name": name, "type": "SMB", "path": path})
        self._persist(drives=self.drives)
        self._reload_drives()

    def _open_drive(self) -> None:
        sel = self.drives_list.curselection()
        if not sel:
            return
        d = self._drives_shown[sel[0]]
        p = Path(d["path"])
        if d.get("type") == "LOCAL" and p.exists():
            self.explorer_path = p
            self._persist(explorer_path=str(p))
            self._explorer_refresh()
            self.nb.select(self.tab_files)
        else:
            messagebox.showinfo(
                APP_TITLE,
                f"Ruta: {d['path']}\n\n"
                "Si es SMB, móntalo en el sistema o usa Interconexión (visor solo lectura).\n"
                "En Linux: gio mount smb://IP/share",
            )

    def _remove_drive(self) -> None:
        sel = self.drives_list.curselection()
        if not sel:
            return
        d = self._drives_shown[sel[0]]
        if d in self.drives:
            self.drives.remove(d)
            self._persist(drives=self.drives)
            self._reload_drives()

    # ─── Explorador ──────────────────────────────────────────
    def _build_explorer(self) -> None:
        f = self.tab_files
        bar = ttk.Frame(f)
        bar.pack(fill=tk.X, padx=8, pady=4)
        ttk.Button(bar, text="↑", command=self._explorer_up).pack(side=tk.LEFT)
        ttk.Button(bar, text="Inicio", command=self._explorer_home).pack(side=tk.LEFT, padx=2)
        ttk.Button(bar, text="Actualizar", command=self._explorer_refresh).pack(side=tk.LEFT, padx=2)
        ttk.Button(bar, text="Copiar", command=lambda: self._explorer_clip(False)).pack(
            side=tk.LEFT, padx=2
        )
        ttk.Button(bar, text="Cortar", command=lambda: self._explorer_clip(True)).pack(
            side=tk.LEFT, padx=2
        )
        ttk.Button(bar, text="Pegar", command=self._explorer_paste).pack(side=tk.LEFT, padx=2)
        ttk.Button(bar, text="Nueva carpeta", command=self._explorer_mkdir).pack(
            side=tk.LEFT, padx=2
        )
        ttk.Button(bar, text="Eliminar", command=self._explorer_delete).pack(side=tk.LEFT, padx=2)
        ttk.Button(bar, text="Abrir", command=self._explorer_open).pack(side=tk.LEFT, padx=2)

        self.explorer_path_var = tk.StringVar(value=str(self.explorer_path))
        ttk.Entry(f, textvariable=self.explorer_path_var).pack(fill=tk.X, padx=8, pady=2)

        cols = ("name", "type", "size", "modified")
        self.explorer_tree = ttk.Treeview(f, columns=cols, show="headings", height=22)
        for c, w in zip(cols, (360, 90, 100, 140)):
            self.explorer_tree.heading(c, text=c.capitalize())
            self.explorer_tree.column(c, width=w)
        self.explorer_tree.pack(fill=tk.BOTH, expand=True, padx=8, pady=4)
        self.explorer_tree.bind("<Double-1>", lambda e: self._explorer_double())
        self._explorer_refresh()

    def _explorer_refresh(self) -> None:
        path = Path(self.explorer_path_var.get() or str(self.explorer_path))
        if not path.is_dir():
            path = Path.home()
        self.explorer_path = path
        self.explorer_path_var.set(str(path))
        self._persist(explorer_path=str(path))
        for i in self.explorer_tree.get_children():
            self.explorer_tree.delete(i)
        try:
            entries = sorted(path.iterdir(), key=lambda p: (not p.is_dir(), p.name.lower()))
        except Exception as e:
            messagebox.showerror(APP_TITLE, str(e))
            return
        self._explorer_entries = entries
        for p in entries:
            try:
                st = p.stat()
                tipo = "Carpeta" if p.is_dir() else "Archivo"
                size = "" if p.is_dir() else fmt_size(st.st_size)
                mod = datetime.fromtimestamp(st.st_mtime).strftime("%Y-%m-%d %H:%M")
                self.explorer_tree.insert("", tk.END, values=(p.name, tipo, size, mod))
            except Exception:
                self.explorer_tree.insert("", tk.END, values=(p.name, "?", "", ""))

    def _explorer_double(self) -> None:
        sel = self.explorer_tree.selection()
        if not sel:
            return
        idx = self.explorer_tree.index(sel[0])
        p = self._explorer_entries[idx]
        if p.is_dir():
            self.explorer_path = p
            self.explorer_path_var.set(str(p))
            self._explorer_refresh()
        else:
            open_path(p)

    def _explorer_up(self) -> None:
        parent = self.explorer_path.parent
        if parent != self.explorer_path:
            self.explorer_path = parent
            self.explorer_path_var.set(str(parent))
            self._explorer_refresh()

    def _explorer_home(self) -> None:
        self.explorer_path = Path.home()
        self.explorer_path_var.set(str(self.explorer_path))
        self._explorer_refresh()

    def _explorer_selected(self) -> list[Path]:
        out = []
        for s in self.explorer_tree.selection():
            idx = self.explorer_tree.index(s)
            out.append(self._explorer_entries[idx])
        return out

    def _explorer_clip(self, cut: bool) -> None:
        self.clipboard_paths = self._explorer_selected()
        self.clipboard_cut = cut
        messagebox.showinfo(APP_TITLE, f"{'Cortados' if cut else 'Copiados'}: {len(self.clipboard_paths)}")

    def _explorer_paste(self) -> None:
        if not self.clipboard_paths:
            return
        dest = self.explorer_path
        for src in self.clipboard_paths:
            try:
                target = dest / src.name
                if src.is_dir():
                    if self.clipboard_cut:
                        shutil.move(str(src), str(target))
                    else:
                        shutil.copytree(src, target)
                else:
                    if self.clipboard_cut:
                        shutil.move(str(src), str(target))
                    else:
                        shutil.copy2(src, target)
            except Exception as e:
                messagebox.showerror(APP_TITLE, str(e))
        if self.clipboard_cut:
            self.clipboard_paths = []
        self._explorer_refresh()

    def _explorer_mkdir(self) -> None:
        name = simpledialog.askstring(APP_TITLE, "Nombre de carpeta:")
        if not name:
            return
        try:
            (self.explorer_path / name).mkdir(exist_ok=False)
            self._explorer_refresh()
        except Exception as e:
            messagebox.showerror(APP_TITLE, str(e))

    def _explorer_delete(self) -> None:
        sel = self._explorer_selected()
        if not sel:
            return
        if not messagebox.askyesno(APP_TITLE, f"¿Eliminar {len(sel)} elemento(s)?"):
            return
        for p in sel:
            try:
                if p.is_dir():
                    shutil.rmtree(p)
                else:
                    p.unlink()
            except Exception as e:
                messagebox.showerror(APP_TITLE, str(e))
        self._explorer_refresh()

    def _explorer_open(self) -> None:
        sel = self._explorer_selected()
        if sel:
            open_path(sel[0])

    # ─── Transferencias ──────────────────────────────────────
    def _build_transfers(self) -> None:
        f = self.tab_xfer
        ttk.Label(f, text="Historial de copias / descargas de interconexión").pack(
            anchor=tk.W, padx=8, pady=6
        )
        ttk.Button(f, text="Actualizar", command=self._reload_transfers).pack(anchor=tk.W, padx=8)
        self.xfer_list = tk.Listbox(f, bg="#131b2e", fg=FG, height=22)
        self.xfer_list.pack(fill=tk.BOTH, expand=True, padx=8, pady=8)
        self._reload_transfers()

    def _log_transfer(self, name: str, src: str, dest: str, status: str = "ok") -> None:
        hist = load_json(HISTORY_FILE, [])
        hist.insert(
            0,
            {
                "name": name,
                "from": src,
                "to": dest,
                "status": status,
                "when": datetime.now().isoformat(timespec="seconds"),
            },
        )
        save_json(HISTORY_FILE, hist[:100])
        self._reload_transfers()

    def _reload_transfers(self) -> None:
        self.xfer_list.delete(0, tk.END)
        for t in load_json(HISTORY_FILE, []):
            self.xfer_list.insert(
                tk.END,
                f"{t.get('when','')} · {t.get('status')} · {t.get('name')} · {t.get('from')} → {t.get('to')}",
            )

    # ─── Backups ─────────────────────────────────────────────
    def _build_backup(self) -> None:
        f = self.tab_backup
        ttk.Label(
            f,
            text="Copia de seguridad simple: elige carpeta origen y destino.",
        ).pack(anchor=tk.W, padx=8, pady=8)
        ttk.Button(f, text="Hacer backup ahora…", command=self._run_backup).pack(
            anchor=tk.W, padx=8, pady=4
        )
        self.backup_log = tk.Text(f, height=18, bg="#131b2e", fg=FG)
        self.backup_log.pack(fill=tk.BOTH, expand=True, padx=8, pady=8)

    def _run_backup(self) -> None:
        src = filedialog.askdirectory(title="Carpeta a respaldar")
        if not src:
            return
        dest_root = filedialog.askdirectory(title="Dónde guardar el backup")
        if not dest_root:
            return
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        dest = Path(dest_root) / f"nexus_backup_{stamp}"
        try:
            shutil.copytree(src, dest)
            msg = f"OK → {dest}"
            self._log_transfer(dest.name, src, str(dest), "backup")
        except Exception as e:
            msg = f"Error: {e}"
        self.backup_log.insert(tk.END, msg + "\n")

    # ─── Interconexión ───────────────────────────────────────
    def _build_interconnect(self) -> None:
        f = self.tab_inter
        nb = ttk.Notebook(f)
        nb.pack(fill=tk.BOTH, expand=True)
        share_f = ttk.Frame(nb)
        view_f = ttk.Frame(nb)
        nb.add(share_f, text=" Exponer mi disco ")
        nb.add(view_f, text=" Ver otro dispositivo ")

        ttk.Label(
            share_f,
            text="Solo lectura · compatible con Android Interconexión · puerto 8765",
        ).pack(anchor=tk.W, padx=8, pady=6)
        row = ttk.Frame(share_f)
        row.pack(fill=tk.X, padx=8)
        ttk.Label(row, text="Carpeta:").pack(side=tk.LEFT)
        self.share_path_var = tk.StringVar(value=str(self.share_root))
        ttk.Entry(row, textvariable=self.share_path_var, width=55).pack(
            side=tk.LEFT, padx=4, fill=tk.X, expand=True
        )
        ttk.Button(row, text="…", command=self._pick_share).pack(side=tk.LEFT)
        row2 = ttk.Frame(share_f)
        row2.pack(fill=tk.X, padx=8, pady=4)
        ttk.Label(row2, text="Puerto:").pack(side=tk.LEFT)
        self.port_var = tk.StringVar(value=str(self.cfg.get("share_port") or DEFAULT_PORT))
        ttk.Entry(row2, textvariable=self.port_var, width=8).pack(side=tk.LEFT, padx=4)
        self.share_status = tk.StringVar(value="Exposición: parada")
        ttk.Label(share_f, textvariable=self.share_status).pack(anchor=tk.W, padx=8)
        self.ips_var = tk.StringVar()
        ttk.Label(share_f, textvariable=self.ips_var, wraplength=900).pack(anchor=tk.W, padx=8)
        br = ttk.Frame(share_f)
        br.pack(padx=8, pady=8)
        ttk.Button(br, text="Iniciar exposición", command=self._start_share).pack(
            side=tk.LEFT, padx=4
        )
        ttk.Button(br, text="Detener", command=self._stop_share).pack(side=tk.LEFT, padx=4)
        self._refresh_ips()

        # View
        ttk.Label(view_f, text="Visor remoto (solo ver/copiar)").pack(anchor=tk.W, padx=8, pady=4)
        crow = ttk.Frame(view_f)
        crow.pack(fill=tk.X, padx=8)
        ttk.Label(crow, text="IP:").pack(side=tk.LEFT)
        self.remote_ip_var = tk.StringVar(value=self.remote_host)
        ttk.Entry(crow, textvariable=self.remote_ip_var, width=22).pack(side=tk.LEFT, padx=4)
        ttk.Label(crow, text="Puerto:").pack(side=tk.LEFT)
        self.remote_port_var = tk.StringVar(value=str(self.remote_port))
        ttk.Entry(crow, textvariable=self.remote_port_var, width=8).pack(side=tk.LEFT, padx=4)
        ttk.Button(crow, text="Conectar", command=self._connect_remote).pack(side=tk.LEFT, padx=4)
        ttk.Button(crow, text="↑", command=self._remote_up).pack(side=tk.LEFT)
        ttk.Button(crow, text="Raíz", command=self._remote_root).pack(side=tk.LEFT, padx=2)
        self.remote_path_var = tk.StringVar(value="/")
        ttk.Label(view_f, textvariable=self.remote_path_var).pack(anchor=tk.W, padx=8)

        # History of IPs
        hist = list(self.cfg.get("remote_history") or [])
        if hist:
            hf = ttk.Frame(view_f)
            hf.pack(fill=tk.X, padx=8)
            ttk.Label(hf, text="Guardadas:").pack(side=tk.LEFT)
            for h in hist[:6]:
                ttk.Button(
                    hf, text=h, command=lambda x=h: self._connect_saved(x)
                ).pack(side=tk.LEFT, padx=2)

        cols = ("name", "type", "size")
        self.remote_tree = ttk.Treeview(view_f, columns=cols, show="headings", height=16)
        for c, w in zip(cols, (420, 100, 100)):
            self.remote_tree.heading(c, text=c.capitalize())
            self.remote_tree.column(c, width=w)
        self.remote_tree.pack(fill=tk.BOTH, expand=True, padx=8, pady=4)
        self.remote_tree.bind("<Double-1>", lambda e: self._remote_double())
        ttk.Button(view_f, text="Descargar seleccionado", command=self._remote_download).pack(
            pady=4
        )

    def _pick_share(self) -> None:
        p = filedialog.askdirectory(initialdir=str(self.share_root))
        if p:
            self.share_root = Path(p)
            self.share_path_var.set(p)

    def _refresh_ips(self) -> None:
        self.ips_var.set("IPs: " + (", ".join(local_ipv4()) or "(no detectadas)"))

    def _start_share_silent(self) -> None:
        try:
            self._start_share(silent=True)
        except Exception:
            pass

    def _start_share(self, silent: bool = False) -> None:
        if self.server is not None:
            if not silent:
                messagebox.showinfo(APP_TITLE, "Ya está expuesta.")
            return
        root = Path(self.share_path_var.get().strip() or str(Path.home()))
        if not root.is_dir():
            messagebox.showerror(APP_TITLE, f"Carpeta inválida: {root}")
            return
        try:
            port = int(self.port_var.get() or DEFAULT_PORT)
        except ValueError:
            messagebox.showerror(APP_TITLE, "Puerto inválido")
            return
        try:
            self.server = start_server(root, port)
        except Exception as e:
            messagebox.showerror(APP_TITLE, str(e))
            return

        def run() -> None:
            assert self.server is not None
            self.server.serve_forever()

        self.server_thread = threading.Thread(target=run, daemon=True)
        self.server_thread.start()
        self.share_root = root
        self._refresh_ips()
        self.share_status.set(f"ACTIVA · {root} · puerto {port}")
        self._persist(
            share_root=str(root), share_port=port, auto_start_share=True
        )
        if not silent:
            messagebox.showinfo(
                APP_TITLE,
                f"Exposición activa (solo lectura)\n{root}\nPuerto {port}\n"
                f"IPs: {', '.join(local_ipv4())}",
            )
        self._refresh_dashboard()

    def _stop_share(self) -> None:
        if self.server:
            try:
                self.server.shutdown()
            except Exception:
                pass
        self.server = None
        self.share_status.set("Exposición: parada")
        self._persist(auto_start_share=False)
        self._refresh_dashboard()

    def _connect_saved(self, key: str) -> None:
        parts = key.split(":")
        self.remote_ip_var.set(parts[0])
        if len(parts) > 1:
            self.remote_port_var.set(parts[1])
        self._connect_remote()

    def _connect_remote(self) -> None:
        host = self.remote_ip_var.get().strip()
        if not host:
            messagebox.showwarning(APP_TITLE, "Introduce IP")
            return
        try:
            port = int(self.remote_port_var.get() or DEFAULT_PORT)
        except ValueError:
            messagebox.showerror(APP_TITLE, "Puerto inválido")
            return
        self.remote_host = host
        self.remote_port = port
        self.path_stack = []
        key = host if port == DEFAULT_PORT else f"{host}:{port}"
        hist = list(self.cfg.get("remote_history") or [])
        if key in hist:
            hist.remove(key)
        hist.insert(0, key)
        self._persist(
            last_remote_host=host,
            last_remote_port=port,
            remote_history=hist[:20],
        )
        self._remote_refresh()

    def _remote_refresh(self) -> None:
        path = "/".join(self.path_stack)
        self.remote_path_var.set("/" + path if path else "/")
        q = f"?path={urllib.parse.quote(path)}" if path else ""
        url = f"http://{self.remote_host}:{self.remote_port}/api/list{q}"
        try:
            with urllib.request.urlopen(url, timeout=10) as resp:
                data = json.loads(resp.read().decode("utf-8"))
        except Exception as e:
            messagebox.showerror(
                APP_TITLE,
                f"No se pudo conectar a {self.remote_host}:{self.remote_port}\n{e}",
            )
            return
        self.remote_entries = data.get("entries") or []
        for i in self.remote_tree.get_children():
            self.remote_tree.delete(i)
        for e in self.remote_entries:
            tipo = "Carpeta" if e.get("dir") else "Archivo"
            size = "" if e.get("dir") else fmt_size(int(e.get("size") or 0))
            self.remote_tree.insert("", tk.END, values=(e.get("name"), tipo, size))

    def _remote_double(self) -> None:
        sel = self.remote_tree.selection()
        if not sel:
            return
        e = self.remote_entries[self.remote_tree.index(sel[0])]
        if e.get("dir"):
            self.path_stack.append(e["name"])
            self._remote_refresh()
        else:
            self._remote_download_entry(e)

    def _remote_up(self) -> None:
        if self.path_stack:
            self.path_stack.pop()
            self._remote_refresh()

    def _remote_root(self) -> None:
        self.path_stack = []
        self._remote_refresh()

    def _remote_download(self) -> None:
        sel = self.remote_tree.selection()
        if not sel:
            return
        e = self.remote_entries[self.remote_tree.index(sel[0])]
        if e.get("dir"):
            self.path_stack.append(e["name"])
            self._remote_refresh()
        else:
            self._remote_download_entry(e)

    def _remote_download_entry(self, e: dict) -> None:
        dest_dir = filedialog.askdirectory(title="Guardar en…")
        if not dest_dir:
            return
        rel = e.get("path") or e.get("name")
        parts = [urllib.parse.quote(p) for p in rel.replace("\\", "/").split("/") if p]
        url = f"http://{self.remote_host}:{self.remote_port}/file/{'/'.join(parts)}"
        dest = Path(dest_dir) / e["name"]
        try:
            urllib.request.urlretrieve(url, dest)
            self._log_transfer(e["name"], url, str(dest))
            messagebox.showinfo(APP_TITLE, f"Guardado:\n{dest}")
            open_path(dest)
        except Exception as ex:
            messagebox.showerror(APP_TITLE, str(ex))

    # ─── Colaborativo ────────────────────────────────────────
    def _build_collab(self) -> None:
        f = self.tab_collab
        ttk.Label(f, text="Espacios de trabajo (locales, como en la app)").pack(
            anchor=tk.W, padx=8, pady=6
        )
        row = ttk.Frame(f)
        row.pack(fill=tk.X, padx=8)
        ttk.Button(row, text="Nuevo espacio", command=self._collab_new).pack(side=tk.LEFT, padx=2)
        ttk.Button(row, text="Actualizar", command=self._collab_reload).pack(side=tk.LEFT, padx=2)
        self.collab_list = tk.Listbox(f, bg="#131b2e", fg=FG, height=18)
        self.collab_list.pack(fill=tk.BOTH, expand=True, padx=8, pady=8)
        self._collab_reload()

    def _collab_reload(self) -> None:
        self.collab_list.delete(0, tk.END)
        spaces = load_json(SPACES_FILE, [])
        for s in spaces:
            self.collab_list.insert(
                tk.END,
                f"{s.get('name')} · código {s.get('invite')} · {s.get('role','owner')}",
            )

    def _collab_new(self) -> None:
        name = simpledialog.askstring(APP_TITLE, "Nombre del espacio:")
        if not name:
            return
        import uuid

        invite = uuid.uuid4().hex[:8].upper()
        spaces = load_json(SPACES_FILE, [])
        spaces.append(
            {
                "name": name,
                "invite": invite,
                "role": "owner",
                "created": datetime.now().isoformat(timespec="seconds"),
            }
        )
        save_json(SPACES_FILE, spaces)
        messagebox.showinfo(APP_TITLE, f"Espacio creado\nCódigo invitación: {invite}")
        self._collab_reload()

    # ─── Memoria ─────────────────────────────────────────────
    def _build_memory(self) -> None:
        f = self.tab_mem
        self.mem_text = tk.Text(f, height=26, bg="#131b2e", fg=FG, font=("Consolas", 10))
        self.mem_text.pack(fill=tk.BOTH, expand=True, padx=8, pady=8)
        ttk.Button(f, text="Actualizar análisis", command=self._refresh_memory).pack(pady=4)
        self._refresh_memory()

    def _refresh_memory(self) -> None:
        lines = [f"Sistema: {platform.platform()}", ""]
        try:
            usage = shutil.disk_usage(str(Path.home()))
            lines.append("Almacenamiento (home):")
            lines.append(f"  Total: {fmt_size(usage.total)}")
            lines.append(f"  Usado: {fmt_size(usage.used)} ({100 * usage.used // usage.total}%)")
            lines.append(f"  Libre: {fmt_size(usage.free)}")
            # simple bar
            pct = int(30 * usage.used / usage.total)
            lines.append("  [" + "#" * pct + "-" * (30 - pct) + "]")
        except Exception as e:
            lines.append(str(e))
        lines.append("")
        try:
            if hasattr(os, "sysconf"):
                lines.append("(RAM detallada: usa htop/free en terminal)")
        except Exception:
            pass
        lines.append(f"Config NexusMount: {CONFIG_DIR}")
        try:
            cfg_size = sum(p.stat().st_size for p in CONFIG_DIR.rglob("*") if p.is_file())
            lines.append(f"Tamaño datos app: {fmt_size(cfg_size)}")
        except Exception:
            pass
        self.mem_text.delete("1.0", tk.END)
        self.mem_text.insert(tk.END, "\n".join(lines))

    # ─── Limpieza ────────────────────────────────────────────
    def _build_cleanup(self) -> None:
        f = self.tab_clean
        ttk.Label(
            f,
            text="Limpieza de cachés y temporales de NexusMount PC (seguro).",
        ).pack(anchor=tk.W, padx=8, pady=8)
        ttk.Button(f, text="Analizar", command=self._clean_scan).pack(anchor=tk.W, padx=8)
        ttk.Button(f, text="Limpiar temporales de la app", command=self._clean_run).pack(
            anchor=tk.W, padx=8, pady=4
        )
        self.clean_text = tk.Text(f, height=16, bg="#131b2e", fg=FG)
        self.clean_text.pack(fill=tk.BOTH, expand=True, padx=8, pady=8)

    def _clean_scan(self) -> None:
        tmp = Path.home() / ".nexusmount" / "tmp"
        cache = Path.home() / ".nexusmount" / "cache"
        lines = []
        for d in (tmp, cache, CONFIG_DIR):
            if not d.exists():
                lines.append(f"{d}: (no existe)")
                continue
            size = sum(p.stat().st_size for p in d.rglob("*") if p.is_file())
            lines.append(f"{d}: {fmt_size(size)}")
        self.clean_text.delete("1.0", tk.END)
        self.clean_text.insert(tk.END, "\n".join(lines))

    def _clean_run(self) -> None:
        removed = 0
        for name in ("tmp", "cache"):
            d = CONFIG_DIR / name
            if d.is_dir():
                shutil.rmtree(d, ignore_errors=True)
                removed += 1
                d.mkdir(parents=True, exist_ok=True)
        messagebox.showinfo(APP_TITLE, f"Limpieza hecha ({removed} carpetas)")
        self._clean_scan()

    # ─── Rigo ────────────────────────────────────────────────
    def _build_rigo(self) -> None:
        f = self.tab_rigo
        ttk.Label(f, text="Rigo · asistente local (comandos básicos)").pack(
            anchor=tk.W, padx=8, pady=4
        )
        row = ttk.Frame(f)
        row.pack(fill=tk.X, padx=8)
        self.rigo_in = tk.StringVar()
        ttk.Entry(row, textvariable=self.rigo_in).pack(side=tk.LEFT, fill=tk.X, expand=True)
        ttk.Button(row, text="Enviar", command=self._rigo_run).pack(side=tk.LEFT, padx=4)
        self.rigo_out = tk.Text(f, height=22, bg="#131b2e", fg=FG)
        self.rigo_out.pack(fill=tk.BOTH, expand=True, padx=8, pady=8)
        self.rigo_out.insert(
            tk.END,
            "Hola, soy Rigo en PC.\nPrueba: ayuda · estado · espacio · abrir explorador · interconexión\n",
        )

    def _rigo_run(self) -> None:
        cmd = self.rigo_in.get().strip().lower()
        self.rigo_out.insert(tk.END, f"\nTú: {cmd}\n")
        if not cmd or "ayuda" in cmd:
            reply = (
                "Comandos: ayuda, estado, espacio, unidades, "
                "abrir explorador, abrir interconexión, backup, limpieza"
            )
        elif "estado" in cmd:
            reply = (
                f"Exposición={'ON' if self.server else 'OFF'} · "
                f"Unidades={len(self.drives)} · IP remota={self.remote_host or '—'}"
            )
        elif "espacio" in cmd:
            u = shutil.disk_usage(str(Path.home()))
            reply = f"Libre {fmt_size(u.free)} / Total {fmt_size(u.total)}"
        elif "unidad" in cmd:
            self.nb.select(self.tab_drives)
            reply = "Abriendo Unidades…"
        elif "explorador" in cmd:
            self.nb.select(self.tab_files)
            reply = "Abriendo Explorador…"
        elif "interconex" in cmd:
            self.nb.select(self.tab_inter)
            reply = "Abriendo Interconexión…"
        elif "backup" in cmd:
            self.nb.select(self.tab_backup)
            reply = "Abriendo Backups…"
        elif "limpieza" in cmd:
            self.nb.select(self.tab_clean)
            reply = "Abriendo Limpieza…"
        else:
            reply = "No entendido. Escribe ayuda."
        self.rigo_out.insert(tk.END, f"Rigo: {reply}\n")
        self.rigo_out.see(tk.END)

    # ─── Ajustes ─────────────────────────────────────────────
    def _build_settings(self) -> None:
        f = self.tab_settings
        ttk.Label(f, text="Ajustes guardados en ~/.nexusmount/pc_config.json").pack(
            anchor=tk.W, padx=8, pady=8
        )
        ttk.Button(
            f, text="Abrir carpeta de configuración", command=lambda: open_path(CONFIG_DIR)
        ).pack(anchor=tk.W, padx=8, pady=2)
        ttk.Button(f, text="Actualizar Dashboard", command=self._refresh_dashboard).pack(
            anchor=tk.W, padx=8, pady=2
        )
        info = tk.Text(f, height=16, bg="#131b2e", fg=MUTED)
        info.pack(fill=tk.BOTH, expand=True, padx=8, pady=8)
        info.insert(
            tk.END,
            "Equivalencia con la APK Android:\n\n"
            "Dashboard → Dashboard\n"
            "Mis Unidades → Unidades\n"
            "Explorador Pro → Explorador\n"
            "Transferencias → Transferencias\n"
            "Backups → Backups\n"
            "Interconexión → Interconexión (exponer + ver)\n"
            "Colaborativo → Colaborativo\n"
            "Análisis memoria → Memoria\n"
            "Limpieza → Limpieza\n"
            "Rigo → Rigo IA\n"
            "Ajustes → Ajustes\n\n"
            "Nota: SMB nativo completo en PC depende del SO "
            "(montaje del sistema). Interconexión no usa Samba.",
        )

    def on_close(self) -> None:
        try:
            self._persist(
                share_root=self.share_path_var.get().strip() or str(self.share_root),
                share_port=int(self.port_var.get() or DEFAULT_PORT),
                last_remote_host=self.remote_ip_var.get().strip(),
                last_remote_port=int(self.remote_port_var.get() or DEFAULT_PORT),
                explorer_path=str(self.explorer_path),
                drives=self.drives,
            )
        except Exception:
            pass
        self.destroy()


def main() -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    app = NexusMountPC()
    app.mainloop()


if __name__ == "__main__":
    main()
