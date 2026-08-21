# NexusMount PC — réplica de escritorio de la APK

Misma estructura de módulos que la app Android:

| Pestaña PC | Equivale en Android |
|------------|---------------------|
| Dashboard | Dashboard |
| Unidades | Mis Unidades |
| Explorador | Explorador Pro |
| Transferencias | Transferencias |
| Backups | Backups |
| Interconexión | Interconexión (visor) |
| Colaborativo | Trabajo colaborativo |
| Memoria | Análisis de memoria |
| Limpieza | Limpieza inteligente |
| Rigo IA | Rigo |
| Ajustes | Ajustes |

## Requisitos

- Python 3.8+ y **tkinter**

```bash
# Ubuntu / Debian / Raspberry Pi OS
sudo apt install python3 python3-tk

# Fedora
sudo dnf install python3 python3-tkinter
```

## Arranque

**Linux / Pi**
```bash
chmod +x iniciar_linux.sh
./iniciar_linux.sh
```

**Windows:** `iniciar_windows.bat`

Configuración guardada en `~/.nexusmount/pc_config.json`
