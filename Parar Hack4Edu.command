#!/usr/bin/env bash
# Para Hack4Edu (los datos se conservan en el volumen de Docker).
# Con HACK4EDU_SIN_TERMINAL=1 (lo pone «Parar Hack4Edu.app») no usa la terminal:
# avisa con una ventana y escribe la salida en hack4edu.log.
cd "$(dirname "$0")"
export PATH="/usr/local/bin:/opt/homebrew/bin:/Applications/Docker.app/Contents/Resources/bin:$PATH"

if [ "${HACK4EDU_SIN_TERMINAL:-0}" != 1 ]; then
  docker compose down && echo "✓ Hack4Edu parado. Los datos se conservan."
  read -r -p "Pulsa Intro para cerrar…"
  exit 0
fi

dialogo() {
  osascript - "$1" "$2" >/dev/null 2>&1 <<'AS'
on run argv
  if item 2 of argv is "caution" then
    display dialog (item 1 of argv) with title "Hack4Edu" buttons {"OK"} default button 1 with icon caution
  else
    display dialog (item 1 of argv) with title "Hack4Edu" buttons {"OK"} default button 1 with icon note
  end if
end run
AS
}

exec >>hack4edu.log 2>&1
printf '\n===== %s · parar =====\n' "$(date '+%Y-%m-%d %H:%M:%S')"
osascript -e 'display notification "Parando Hack4Edu…" with title "Hack4Edu"' >/dev/null 2>&1
if docker compose down; then
  dialogo "Hack4Edu se ha parado. Los datos se conservan." note
else
  dialogo "No se pudo parar Hack4Edu (¿está abierto Docker Desktop?).

Detalles en: $(pwd)/hack4edu.log" caution
  exit 1
fi
