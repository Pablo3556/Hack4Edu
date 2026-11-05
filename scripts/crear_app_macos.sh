#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Crea Hack4Edu.app en la raíz del repositorio: un icono para el Dock o el
# Escritorio que abre Hack4Edu.command en el Terminal.
#   bash scripts/crear_app_macos.sh
# La .app debe quedarse dentro de la carpeta del proyecto (busca el .command a su lado).
# ---------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")/.."
RAIZ="$(pwd)"

rm -rf Hack4Edu.app
osacompile -o Hack4Edu.app <<'APPLESCRIPT'
set carpeta to do shell script "dirname " & quoted form of POSIX path of (path to me)
tell application "Terminal"
	activate
	do script "bash " & quoted form of (carpeta & "/Hack4Edu.command")
end tell
APPLESCRIPT

# Icono de la app (Iconos_AdAstra/macos/AdAstra.icns, generado desde icono-macos.svg)
cp Iconos_AdAstra/macos/AdAstra.icns Hack4Edu.app/Contents/Resources/applet.icns
# osacompile añade Assets.car con el icono genérico de AppleScript, que macOS prioriza
# sobre applet.icns: lo quitamos para que se vea el nuestro.
rm -f Hack4Edu.app/Contents/Resources/Assets.car
/usr/libexec/PlistBuddy -c "Delete :CFBundleIconName" Hack4Edu.app/Contents/Info.plist 2>/dev/null || true
codesign --force --deep --sign - Hack4Edu.app 2>/dev/null   # refirma en local tras cambiar el icono
touch Hack4Edu.app   # para que Finder refresque el icono

echo "✓ Creada $RAIZ/Hack4Edu.app"
echo "  Arrástrala al Dock para arrancar Hack4Edu con un clic (no la saques de esta carpeta)."
