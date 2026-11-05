#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Crea accesos directos para arrancar y parar Hack4Edu con el icono de Ad Astra en el menú de
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

# Sin terminal: el script avisa con notificaciones y ventanas, y deja la salida en hack4edu.log
mkdir -p "$DATOS/applications"
DESKTOP="$DATOS/applications/hack4edu.desktop"
cat > "$DESKTOP" <<DESK
[Desktop Entry]
Type=Application
Name=Hack4Edu
Comment=Ad Astra · La escuela que aprende de sus datos
Exec=env HACK4EDU_SIN_TERMINAL=1 bash "$RAIZ/Hack4Edu-linux.sh"
Path=$RAIZ
Icon=adastra
Terminal=false
Categories=Education;
DESK
chmod +x "$DESKTOP"
# Acceso para pararlo
PARAR="$DATOS/applications/hack4edu-parar.desktop"
cat > "$PARAR" <<DESK
[Desktop Entry]
Type=Application
Name=Parar Hack4Edu
Comment=Para Hack4Edu (los datos se conservan)
Exec=env HACK4EDU_SIN_TERMINAL=1 bash "$RAIZ/Parar-Hack4Edu-linux.sh"
Path=$RAIZ
Icon=adastra
Terminal=false
Categories=Education;
DESK
chmod +x "$PARAR"
update-desktop-database -q "$DATOS/applications" 2>/dev/null || true

ESCRITORIO="$(xdg-user-dir DESKTOP 2>/dev/null || echo "$HOME/Desktop")"
if [ -d "$ESCRITORIO" ]; then
  cp "$DESKTOP" "$ESCRITORIO/Hack4Edu.desktop"
  chmod +x "$ESCRITORIO/Hack4Edu.desktop"
  gio set "$ESCRITORIO/Hack4Edu.desktop" metadata::trusted true 2>/dev/null || true
  cp "$PARAR" "$ESCRITORIO/Parar-Hack4Edu.desktop"
  chmod +x "$ESCRITORIO/Parar-Hack4Edu.desktop"
  gio set "$ESCRITORIO/Parar-Hack4Edu.desktop" metadata::trusted true 2>/dev/null || true
fi

echo "✓ Accesos directos creados: busca «Hack4Edu» y «Parar Hack4Edu» en el menú de aplicaciones."
