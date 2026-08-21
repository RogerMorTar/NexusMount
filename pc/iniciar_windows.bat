@echo off
chcp 65001 >nul
cd /d "%~dp0"
python --version >nul 2>&1
if errorlevel 1 (
  echo Necesitas Python 3 instalado: https://www.python.org/downloads/
  echo Marca "Add Python to PATH" al instalar.
  pause
  exit /b 1
)
python nexusmount_pc.py
pause
