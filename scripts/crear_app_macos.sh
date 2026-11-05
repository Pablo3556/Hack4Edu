#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Crea Hack4Edu.app y «Parar Hack4Edu.app» en la raíz del repositorio: iconos para
# el Dock o el Escritorio que ejecutan Hack4Edu.command / «Parar Hack4Edu.command» en
# segundo plano, sin abrir el Terminal (avisan con notificaciones y ventanas; la
# salida queda en hack4edu.log).
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
	do shell script "cd " & quoted form of carpeta & " && HACK4EDU_SIN_TERMINAL=1 bash './$script' >/dev/null 2>&1"
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

crear_app "Hack4Edu" "Hack4Edu.command"
crear_app "Parar Hack4Edu" "Parar Hack4Edu.command"

echo "✓ Creadas $RAIZ/Hack4Edu.app y «Parar Hack4Edu.app»"
echo "  Arrástralas al Dock para arrancar y parar Hack4Edu con un clic (no las saques de esta carpeta)."
