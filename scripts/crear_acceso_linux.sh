#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Crea un acceso directo a Hack4Edu con el icono de Ad Astra en el menú de
# aplicaciones (y en el Escritorio, si existe).
#   bash scripts/crear_acceso_linux.sh
# El acceso apunta a esta carpeta: no la muevas después de crearlo.
# ---------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")/.."
RAIZ="$(pwd)"
DATOS="${XDG_DATA_HOME:-$HOME/.local/share}"

# Icono en todos los tamaños del tema hicolor
cp -R Iconos_AdAstra/linux/hicolor "$DATOS/icons/" 2>/dev/null || {
  mkdir -p "$DATOS/icons"; cp -R Iconos_AdAstra/linux/hicolor "$DATOS/icons/"; }
gtk-update-icon-cache -q -t "$DATOS/icons/hicolor" 2>/dev/null || true

# Terminal=true: abre un terminal para ver el progreso del arranque
mkdir -p "$DATOS/applications"
DESKTOP="$DATOS/applications/hack4edu.desktop"
cat > "$DESKTOP" <<DESK
[Desktop Entry]
Type=Application
Name=Hack4Edu
Comment=Ad Astra · La escuela que aprende de sus datos
Exec=bash "$RAIZ/Hack4Edu-linux.sh"
Path=$RAIZ
Icon=adastra
Terminal=true
Categories=Education;
DESK
chmod +x "$DESKTOP"
update-desktop-database -q "$DATOS/applications" 2>/dev/null || true

ESCRITORIO="$(xdg-user-dir DESKTOP 2>/dev/null || echo "$HOME/Desktop")"
if [ -d "$ESCRITORIO" ]; then
  cp "$DESKTOP" "$ESCRITORIO/Hack4Edu.desktop"
  chmod +x "$ESCRITORIO/Hack4Edu.desktop"
  gio set "$ESCRITORIO/Hack4Edu.desktop" metadata::trusted true 2>/dev/null || true
fi

echo "✓ Acceso directo creado: busca «Hack4Edu» en el menú de aplicaciones."
