#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Crea accesos directos para arrancar y parar Ad Astra con su icono en el menú de
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

# Sin terminal: el script avisa con notificaciones y ventanas, y deja la salida en adastra.log
mkdir -p "$DATOS/applications"
DESKTOP="$DATOS/applications/adastra.desktop"
cat > "$DESKTOP" <<DESK
[Desktop Entry]
Type=Application
Name=Ad Astra
Comment=Ad Astra · La escuela que aprende de sus datos
Exec=env ADASTRA_SIN_TERMINAL=1 bash "$RAIZ/AdAstra-linux.sh"
Path=$RAIZ
Icon=adastra
Terminal=false
Categories=Education;
DESK
chmod +x "$DESKTOP"
# Acceso para pararlo
PARAR="$DATOS/applications/adastra-parar.desktop"
cat > "$PARAR" <<DESK
[Desktop Entry]
Type=Application
Name=Parar Ad Astra
Comment=Para Ad Astra (los datos se conservan)
Exec=env ADASTRA_SIN_TERMINAL=1 bash "$RAIZ/Parar-AdAstra-linux.sh"
Path=$RAIZ
Icon=adastra
Terminal=false
Categories=Education;
DESK
chmod +x "$PARAR"
update-desktop-database -q "$DATOS/applications" 2>/dev/null || true

ESCRITORIO="$(xdg-user-dir DESKTOP 2>/dev/null || echo "$HOME/Desktop")"
if [ -d "$ESCRITORIO" ]; then
  cp "$DESKTOP" "$ESCRITORIO/AdAstra.desktop"
  chmod +x "$ESCRITORIO/AdAstra.desktop"
  gio set "$ESCRITORIO/AdAstra.desktop" metadata::trusted true 2>/dev/null || true
  cp "$PARAR" "$ESCRITORIO/Parar-AdAstra.desktop"
  chmod +x "$ESCRITORIO/Parar-AdAstra.desktop"
  gio set "$ESCRITORIO/Parar-AdAstra.desktop" metadata::trusted true 2>/dev/null || true
fi

echo "✓ Accesos directos creados: busca «Ad Astra» y «Parar Ad Astra» en el menú de aplicaciones."
