#!/usr/bin/env bash
# Para Hack4Edu (los datos se conservan en el volumen de Docker).
# Con HACK4EDU_SIN_TERMINAL=1 (lo pone el acceso directo) no usa la terminal: avisa
# con zenity/kdialog/notify-send si están instalados y escribe la salida en hack4edu.log.
cd "$(dirname "$0")"

if [ "${HACK4EDU_SIN_TERMINAL:-0}" != 1 ]; then
  docker compose down && echo "✓ Hack4Edu parado. Los datos se conservan."
  read -r -p "Pulsa Intro para cerrar…"
  exit 0
fi

# Ventana de diálogo: $1 texto, $2 tipo (error | info)
dialogo() {
  if command -v zenity >/dev/null; then zenity "--$2" --title=Hack4Edu --no-wrap --text="$1" 2>/dev/null
  elif command -v kdialog >/dev/null; then
    if [ "$2" = error ]; then kdialog --title Hack4Edu --error "$1"; else kdialog --title Hack4Edu --msgbox "$1"; fi
  elif command -v notify-send >/dev/null; then notify-send -a Hack4Edu Hack4Edu "$1"
  fi
}

exec >>hack4edu.log 2>&1
printf '\n===== %s · parar =====\n' "$(date '+%Y-%m-%d %H:%M:%S')"
command -v notify-send >/dev/null && notify-send -a Hack4Edu Hack4Edu "Parando Hack4Edu…"
if docker compose down; then
  dialogo "Hack4Edu se ha parado. Los datos se conservan." info
else
  dialogo "No se pudo parar Hack4Edu (¿está arrancado Docker?).

Detalles en: $(pwd)/hack4edu.log" error
  exit 1
fi
