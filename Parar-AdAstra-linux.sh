#!/usr/bin/env bash
# Para Ad Astra (los datos se conservan en el volumen de Docker).
# Con ADASTRA_SIN_TERMINAL=1 (lo pone el acceso directo) no usa la terminal: avisa
# con zenity/kdialog/notify-send si están instalados y escribe la salida en adastra.log.
cd "$(dirname "$0")"

if [ "${ADASTRA_SIN_TERMINAL:-0}" != 1 ]; then
  docker compose down && echo "✓ Ad Astra parado. Los datos se conservan."
  read -r -p "Pulsa Intro para cerrar…"
  exit 0
fi

# Ventana de diálogo: $1 texto, $2 tipo (error | info)
dialogo() {
  if command -v zenity >/dev/null; then zenity "--$2" --title="Ad Astra" --no-wrap --text="$1" 2>/dev/null
  elif command -v kdialog >/dev/null; then
    if [ "$2" = error ]; then kdialog --title "Ad Astra" --error "$1"; else kdialog --title "Ad Astra" --msgbox "$1"; fi
  elif command -v notify-send >/dev/null; then notify-send -a "Ad Astra" "Ad Astra" "$1"
  fi
}

exec >>adastra.log 2>&1
printf '\n===== %s · parar =====\n' "$(date '+%Y-%m-%d %H:%M:%S')"
command -v notify-send >/dev/null && notify-send -a "Ad Astra" "Ad Astra" "Parando Ad Astra…"
if docker compose down; then
  dialogo "Ad Astra se ha parado. Los datos se conservan." info
else
  dialogo "No se pudo parar Ad Astra (¿está arrancado Docker?).

Detalles en: $(pwd)/adastra.log" error
  exit 1
fi
