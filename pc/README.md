# NexusMount PC — Interconexión

Aplicación de escritorio (**Windows / Linux / macOS**) compatible con **NexusMount Android**.

## Qué hace

1. **Exponer mi disco** — Comparte una carpeta en la red (Wi‑Fi o Tailscale) en **solo lectura** (puerto **8765**).
2. **Ver otro dispositivo** — Conecta por IP a un móvil/PC con exposición activa: listar, abrir y copiar. **No** elimina ni modifica.

**Samba no es necesario** para la interconexión. Samba sigue siendo el camino para editar/borrar en red.

## Requisitos

- **Python 3.8+**
- Módulo **tkinter** (interfaz gráfica)

### Instalar Python + tkinter

| Sistema | Comando |
|---------|---------|
| **Ubuntu / Debian** | `sudo apt install python3 python3-tk` |
| **Fedora** | `sudo dnf install python3 python3-tkinter` |
| **Arch** | `sudo pacman -S python tk` |
| **Windows** | [python.org](https://www.python.org/downloads/) (marca *Add to PATH*) |
| **macOS** | `brew install python-tk` o el instalador oficial |

## Uso

### Linux
```bash
cd pc
chmod +x iniciar_linux.sh
./iniciar_linux.sh
```
O directamente:
```bash
python3 nexusmount_pc.py
```

### Windows
Doble clic en `iniciar_windows.bat`  
o: `python nexusmount_pc.py`

### macOS
```bash
python3 nexusmount_pc.py
```

## Con el móvil

1. En el **PC**: pestaña *Exponer mi disco* → elige carpeta → *Iniciar exposición*.
2. Copia la **IP** (o la `100.x` de Tailscale).
3. En el **móvil**: *Interconexión* → *Conectar por IP* → misma IP y puerto **8765**.

## Firewall (Linux)

Si no conectan desde el móvil:
```bash
# Ejemplo ufw
sudo ufw allow 8765/tcp
```

## API (igual que Android)

- `GET /api/info`
- `GET /api/list?path=`
- `GET /file/ruta/al/archivo`
