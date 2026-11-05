@echo off
cd /d "%~dp0"
docker compose down && echo Ad Astra parado. Los datos se conservan.
pause
