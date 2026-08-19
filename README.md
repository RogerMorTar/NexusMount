# NexusMount Android nativo v4.5

## Módulos implementados

### Gestión de archivos
- Explorador Pro: copiar, cortar, pegar, mover, borrar, renombrar
- ZIP crear / extraer
- Búsqueda por nombre (extensible a fechas/tamaños)
- SMB real (smbj) — conectar shares por IP (incl. Tailscale 100.x)

### IA
- Nexus Command AI local (ES): lista, busca, crea carpeta, borra, zip, unzip, backup

### Seguridad
- TOTP 2FA (generar secreto + verificar código)
- Monitor de eventos (fuerza bruta, rutas sensibles)

### Backups
- Instantáneas ZIP + listado + restauración

### Red
- Detección real de Tailscale (instalado, VPN, IPs 100.x)
- Guías de setup Google Drive / S3 / WebDAV (APIs requieren credenciales)

### Transferencias
- Historial con progreso al copiar/mover

## Compilar APK

1. Android Studio → Open `NexusMountAndroid`
2. Build → Build APK(s)  
   o `./gradlew assembleDebug`
3. APK: `app/build/outputs/apk/debug/app-debug.apk`

## Limitaciones honestas

| Ítem | Estado |
|------|--------|
| ACL POSIX/NTFS avanzados | No (Android sandbox) |
| YubiKey | No (requiere NFC/USB libs) |
| Google Drive / S3 API live | Solo guía de configuración |
| IA cloud Gemini/OpenAI | No (añadir API keys del usuario) |
| AVX2 orquestador | No aplica en móvil igual que en PC |
| Renombrado masivo | Se puede extender desde search |
| TAR | Solo ZIP por ahora |

