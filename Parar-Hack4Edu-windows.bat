@echo off
cd /d "%~dp0"
docker compose down && echo Hack4Edu parado. Los datos se conservan.
pause
