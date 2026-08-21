#!/usr/bin/env bash
# NexusMount PC — Linux / macOS
set -e
cd "$(dirname "$0")"

if command -v python3 >/dev/null 2>&1; then
  PY=python3
elif command -v python >/dev/null 2>&1; then
  PY=python
else
  echo "Necesitas Python 3."
  echo "  Debian/Ubuntu:  sudo apt install python3 python3-tk"
  echo "  Fedora:         sudo dnf install python3 python3-tkinter"
  echo "  Arch:           sudo pacman -S python tk"
  exit 1
fi

# tkinter check
if ! $PY -c "import tkinter" 2>/dev/null; then
  echo "Falta el módulo tkinter (interfaz gráfica)."
  echo "  Debian/Ubuntu:  sudo apt install python3-tk"
  echo "  Fedora:         sudo dnf install python3-tkinter"
  echo "  Arch:           sudo pacman -S tk"
  exit 1
fi

echo "Iniciando NexusMount PC…"
exec $PY nexusmount_pc.py
