# NexusMount PC — Interconexión

Aplicación de escritorio (Windows / Linux / macOS) compatible con **NexusMount Android**.

## Qué hace

1. **Exponer mi disco** — Comparte una carpeta en la red (Wi‑Fi o Tailscale) en **solo lectura** (puerto **8765**).
2. **Ver otro dispositivo** — Conecta por IP a un móvil/PC con exposición activa: listar, abrir y copiar. **No** elimina ni modifica.

**Samba no es necesario** para esto. Samba sigue siendo el camino para editar/borrar en red.

## Requisitos

- Python 3.8+ ([python.org](https://www.python.org/downloads/))
- En Windows: marca *Add Python to PATH* al instalar

## Uso

### Windows
Doble clic en `iniciar_windows.bat`  
o en terminal:

```bat
cd pc
python nexusmount_pc.py
```

### Linux / macOS

```bash
cd pc
python3 nexusmount_pc.py
```

## Con el móvil

1. En el **PC**: pestaña *Exponer mi disco* → elige carpeta → *Iniciar exposición*.
2. Copia la **IP** (o la `100.x` de Tailscale).
3. En el **móvil**: *Interconexión* → *Conectar por IP* → misma IP y puerto **8765**.

Al revés igual: expón en el móvil y conecta desde el PC en *Ver otro dispositivo*.

## Firewall

Si no conecta, permite Python / puerto **8765** en el firewall de Windows.

## API (misma que Android)

- `GET /api/info`
- `GET /api/list?path=`
- `GET /file/ruta/al/archivo`
