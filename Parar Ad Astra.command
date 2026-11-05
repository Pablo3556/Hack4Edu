#!/usr/bin/env bash
# Para Ad Astra (los datos se conservan en el volumen de Docker).
# Con ADASTRA_SIN_TERMINAL=1 (lo pone «Parar Ad Astra.app») no usa la terminal:
# avisa con una ventana y escribe la salida en adastra.log.
cd "$(dirname "$0")"
export PATH="/usr/local/bin:/opt/homebrew/bin:/Applications/Docker.app/Contents/Resources/bin:$PATH"

if [ "${ADASTRA_SIN_TERMINAL:-0}" != 1 ]; then
  docker compose down && echo "✓ Ad Astra parado. Los datos se conservan."
  read -r -p "Pulsa Intro para cerrar…"
  exit 0
fi

dialogo() {
  osascript - "$1" "$2" >/dev/null 2>&1 <<'AS'
on run argv
  if item 2 of argv is "caution" then
    display dialog (item 1 of argv) with title "Ad Astra" buttons {"OK"} default button 1 with icon caution
  else
    display dialog (item 1 of argv) with title "Ad Astra" buttons {"OK"} default button 1 with icon note
  end if
end run
AS
}

exec >>adastra.log 2>&1
printf '\n===== %s · parar =====\n' "$(date '+%Y-%m-%d %H:%M:%S')"
osascript -e 'display notification "Parando Ad Astra…" with title "Ad Astra"' >/dev/null 2>&1
if docker compose down; then
  dialogo "Ad Astra se ha parado. Los datos se conservan." note
else
  dialogo "No se pudo parar Ad Astra (¿está abierto Docker Desktop?).

Detalles en: $(pwd)/adastra.log" caution
  exit 1
fi
