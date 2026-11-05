#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Crea Ad Astra.app y «Parar Ad Astra.app» en la raíz del repositorio: iconos para
# el Dock o el Escritorio que ejecutan Ad Astra.command / «Parar Ad Astra.command» en
# segundo plano, sin abrir el Terminal (avisan con notificaciones y ventanas; la
# salida queda en adastra.log).
#   bash scripts/crear_app_macos.sh
# Las .app deben quedarse dentro de la carpeta del proyecto (buscan el .command a su lado).
# ---------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")/.."
RAIZ="$(pwd)"

# crear_app <nombre de la .app> <script .command que ejecuta>
crear_app() {
  local app="$1.app" script="$2"
  rm -rf "$app"
  osacompile -o "$app" <<APPLESCRIPT
set carpeta to do shell script "dirname " & quoted form of POSIX path of (path to me)
try
	-- El icono rebota en el Dock mientras trabaja; los avisos los muestra el propio script
	do shell script "cd " & quoted form of carpeta & " && ADASTRA_SIN_TERMINAL=1 bash './$script' >/dev/null 2>&1"
end try
APPLESCRIPT

  # Icono de la app (Iconos_AdAstra/macos/AdAstra.icns, generado desde icono-macos.svg)
  cp Iconos_AdAstra/macos/AdAstra.icns "$app/Contents/Resources/applet.icns"
  # osacompile añade Assets.car con el icono genérico de AppleScript, que macOS prioriza
  # sobre applet.icns: lo quitamos para que se vea el nuestro.
  rm -f "$app/Contents/Resources/Assets.car"
  /usr/libexec/PlistBuddy -c "Delete :CFBundleIconName" "$app/Contents/Info.plist" 2>/dev/null || true
  codesign --force --deep --sign - "$app" 2>/dev/null   # refirma en local tras cambiar el icono
  touch "$app"   # para que Finder refresque el icono
}

crear_app "Ad Astra" "Ad Astra.command"
crear_app "Parar Ad Astra" "Parar Ad Astra.command"

echo "✓ Creadas $RAIZ/Ad Astra.app y «Parar Ad Astra.app»"
echo "  Arrástralas al Dock para arrancar y parar Ad Astra con un clic (no las saques de esta carpeta)."
