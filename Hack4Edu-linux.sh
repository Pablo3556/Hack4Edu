#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Hack4Edu · arranque con un clic (Linux)
#   1. Comprueba Docker y Ollama (si el binario está en el PATH, lo arranca).
#   2. Crea .env con contraseñas aleatorias la primera vez.
#   3. Descarga los modelos que falten.
#   4. Arranca el contenedor y abre el navegador.
# Ejecútalo con: bash Hack4Edu-linux.sh  (o doble clic, si tu gestor de archivos lo permite)
# Con HACK4EDU_SIN_TERMINAL=1 (lo pone el acceso directo) no usa la terminal: avisa
# con notify-send y zenity/kdialog si están instalados, y escribe la salida en hack4edu.log.
# ---------------------------------------------------------------------
set -uo pipefail
cd "$(dirname "$0")"

SIN_TERMINAL="${HACK4EDU_SIN_TERMINAL:-0}"
if [ "$SIN_TERMINAL" = 1 ]; then
  exec >>hack4edu.log 2>&1
  printf '\n===== %s =====\n' "$(date '+%Y-%m-%d %H:%M:%S')"
fi

# Ventana de diálogo: $1 texto, $2 tipo (error | info)
dialogo() {
  if command -v zenity >/dev/null; then zenity "--${2:-info}" --title=Hack4Edu --no-wrap --text="$1" 2>/dev/null
  elif command -v kdialog >/dev/null; then
    if [ "${2:-info}" = error ]; then kdialog --title Hack4Edu --error "$1"; else kdialog --title Hack4Edu --msgbox "$1"; fi
  elif command -v notify-send >/dev/null; then notify-send -a Hack4Edu Hack4Edu "$1"
  fi
}
notificar() { [ "$SIN_TERMINAL" = 1 ] && command -v notify-send >/dev/null && notify-send -a Hack4Edu Hack4Edu "$1"; return 0; }
esperar_intro() { [ "$SIN_TERMINAL" = 1 ] || read -r -p "$1"; }

paso()  { printf '\n\033[1;34m▸ %s\033[0m\n' "$*"; }
fallo() {
  printf '\n\033[1;31m✗ %s\033[0m\n' "$1"
  [ "$SIN_TERMINAL" = 1 ] && dialogo "$1"$'\n\n'"Detalles en: $(pwd)/hack4edu.log" error
  esperar_intro "Pulsa Intro para cerrar…"
  exit 1
}
notificar "Arrancando Hack4Edu… Te avisaremos cuando esté lista."
clave() { command -v openssl >/dev/null 2>&1 && openssl rand -hex 12 || head -c32 /dev/urandom | od -An -tx1 | tr -d ' \n' | cut -c1-24; }

# ---- 1. Docker -------------------------------------------------------
paso "Comprobando Docker"
command -v docker >/dev/null || fallo "Falta Docker. Instálalo (https://docs.docker.com/engine/install/) y vuelve a intentarlo."
docker info >/dev/null 2>&1 || fallo "Docker no responde. Arráncalo (systemctl start docker, o abre Docker Desktop) y vuelve a intentarlo."
echo "Docker listo"

# ---- 2. Ollama -------------------------------------------------------
paso "Comprobando Ollama"
ollama_ok() { curl -sf -o /dev/null http://localhost:11434/api/tags; }
if ! ollama_ok; then
  if command -v ollama >/dev/null; then
    (ollama serve >/dev/null 2>&1 &)
  else
    fallo "Falta Ollama. Instálalo (https://ollama.com/download) y vuelve a abrir Hack4Edu."
  fi
  for _ in $(seq 1 30); do ollama_ok && break; sleep 1; done
  ollama_ok || fallo "Ollama no responde en localhost:11434."
fi
echo "Ollama listo"

# ---- 3. Configuración -------------------------------------------------
if [ ! -f .env ]; then
  paso "Creando .env con contraseñas aleatorias"
  {
    echo "# Generado por Hack4Edu-linux.sh el $(date '+%Y-%m-%d %H:%M')"
    echo "POSTGRES_PASSWORD=$(clave)"
    echo "ESCUELA_ADMIN_PASSWORD=$(clave)"
    echo "APP_LECTOR_PASSWORD=$(clave)"
    echo "APP_INGESTA_PASSWORD=$(clave)"
    echo "DOCENTE_PASSWORD=$(clave)"
    echo "DIRECCION_PASSWORD=$(clave)"
    echo "ADMINISTRACION_PASSWORD=$(clave)"
    echo "SESION_CLAVE=$(clave)$(clave)"
  } > .env
  chmod 600 .env
fi
# Variables añadidas en versiones posteriores: se completan en un .env antiguo
for v in ADMINISTRACION_PASSWORD SESION_CLAVE; do
  grep -q "^$v=." .env || echo "$v=$(clave)" >> .env
done
set -a; source .env; set +a
PUERTO="${APP_PUERTO:-8080}"

# ---- 4. Modelos ------------------------------------------------------
paso "Comprobando modelos (la primera vez tarda unos minutos)"
for m in "${OLLAMA_CHAT_MODEL:-llama3.2:3b}" "${OLLAMA_EMBEDDING_MODEL:-embeddinggemma}"; do
  if ollama list 2>/dev/null | awk 'NR>1{print $1}' | grep -qx -e "$m" -e "$m:latest"; then
    echo "✓ $m"
  else
    notificar "Descargando el modelo $m (puede tardar unos minutos)…"
    ollama pull "$m" || fallo "No se pudo descargar el modelo $m."
  fi
done

# ---- 5. Aplicación ---------------------------------------------------
paso "Arrancando Hack4Edu (la primera vez construye la imagen: varios minutos)"
notificar "Preparando la aplicación (la primera vez tarda varios minutos)…"
docker compose up -d --build || fallo "docker compose ha fallado. Revisa los mensajes de arriba."

paso "Esperando a que la aplicación responda"
for _ in $(seq 1 150); do
  curl -sf -o /dev/null "http://localhost:$PUERTO/login.html" && break
  sleep 2
done
curl -sf -o /dev/null "http://localhost:$PUERTO/login.html" \
  || fallo "La aplicación no responde. Mira los logs con: docker compose logs app"

(command -v xdg-open >/dev/null && xdg-open "http://localhost:$PUERTO" >/dev/null 2>&1 &) || true

if [ "$SIN_TERMINAL" = 1 ]; then
  dialogo "Hack4Edu está en marcha: http://localhost:$PUERTO

Usuarios (inicia sesión en la web con uno de ellos):
   docente  /  $DOCENTE_PASSWORD
   direccion  /  $DIRECCION_PASSWORD
   administracion  /  $ADMINISTRACION_PASSWORD

La primera vez, los datos de demostración y el asistente se preparan
en segundo plano durante unos minutos. Para pararlo, abre «Parar Hack4Edu»." info
  exit 0
fi

cat <<EOF

$(printf '\033[1;32m✓ Hack4Edu está en marcha: http://localhost:%s\033[0m' "$PUERTO")

  Usuarios (inicia sesión en la web con uno de ellos):
    docente         / $DOCENTE_PASSWORD
    direccion       / $DIRECCION_PASSWORD
    administracion  / $ADMINISTRACION_PASSWORD

  La primera vez, los datos de demostración y el asistente se preparan en segundo
  plano durante unos minutos. Para pararlo, ejecuta «Parar-Hack4Edu-linux.sh».

EOF
read -r -p "Pulsa Intro para cerrar esta ventana (la app sigue funcionando)…"
