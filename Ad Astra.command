#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Ad Astra · arranque con doble clic (macOS)
#   1. Comprueba Docker Desktop y Ollama (y los abre si están cerrados).
#   2. Crea .env con contraseñas aleatorias la primera vez.
#   3. Descarga los modelos que falten.
#   4. Arranca el contenedor y abre el navegador.
# Con ADASTRA_SIN_TERMINAL=1 (lo pone Ad Astra.app) no usa la terminal:
# avisa con notificaciones y ventanas, y escribe la salida en adastra.log.
# ---------------------------------------------------------------------
set -uo pipefail
cd "$(dirname "$0")"
export PATH="/usr/local/bin:/opt/homebrew/bin:/Applications/Docker.app/Contents/Resources/bin:$PATH"

SIN_TERMINAL="${ADASTRA_SIN_TERMINAL:-0}"
if [ "$SIN_TERMINAL" = 1 ]; then
  exec >>adastra.log 2>&1
  printf '\n===== %s =====\n' "$(date '+%Y-%m-%d %H:%M:%S')"
fi

# Ventana de diálogo: $1 texto, $2 icono (caution | note). El texto va como argumento
# para no tener que escapar comillas.
dialogo() {
  osascript - "$1" "${2:-note}" >/dev/null 2>&1 <<'AS'
on run argv
  if item 2 of argv is "caution" then
    display dialog (item 1 of argv) with title "Ad Astra" buttons {"OK"} default button 1 with icon caution
  else
    display dialog (item 1 of argv) with title "Ad Astra" buttons {"OK"} default button 1 with icon note
  end if
end run
AS
}
# Notificación en la esquina (solo sin terminal; con terminal ya se ve el progreso)
notificar() {
  [ "$SIN_TERMINAL" = 1 ] || return 0
  osascript - "$1" >/dev/null 2>&1 <<'AS'
on run argv
  display notification (item 1 of argv) with title "Ad Astra"
end run
AS
}
esperar_intro() { [ "$SIN_TERMINAL" = 1 ] || read -r -p "$1"; }

paso()  { printf '\n\033[1;34m▸ %s\033[0m\n' "$*"; }
fallo() {
  printf '\n\033[1;31m✗ %s\033[0m\n' "$1"
  local texto="$1"
  [ "$SIN_TERMINAL" = 1 ] && texto="$1"$'\n\n'"Detalles en: $(pwd)/adastra.log"
  dialogo "$texto" caution
  [ -n "${2:-}" ] && open "$2"
  esperar_intro "Pulsa Intro para cerrar…"
  exit 1
}

notificar "Arrancando Ad Astra… Te avisaremos cuando esté lista."

# ---- 1. Docker -------------------------------------------------------
paso "Comprobando Docker"
command -v docker >/dev/null || fallo "Falta Docker Desktop. Instálalo y vuelve a abrir Ad Astra." "https://www.docker.com/products/docker-desktop/"
if ! docker info >/dev/null 2>&1; then
  echo "Abriendo Docker Desktop…"
  open -a Docker || fallo "No se pudo abrir Docker Desktop."
  for _ in $(seq 1 90); do docker info >/dev/null 2>&1 && break; sleep 2; done
  docker info >/dev/null 2>&1 || fallo "Docker Desktop no ha terminado de arrancar. Ábrelo a mano y vuelve a intentarlo."
fi
echo "Docker listo"

# ---- 2. Ollama -------------------------------------------------------
paso "Comprobando Ollama"
ollama_ok() { curl -sf -o /dev/null http://localhost:11434/api/tags; }
if ! ollama_ok; then
  if [ -d /Applications/Ollama.app ]; then open -a Ollama
  elif command -v ollama >/dev/null; then (ollama serve >/dev/null 2>&1 &)
  else fallo "Falta Ollama. Instálalo y vuelve a abrir Ad Astra." "https://ollama.com/download"
  fi
  for _ in $(seq 1 30); do ollama_ok && break; sleep 1; done
  ollama_ok || fallo "Ollama no responde en localhost:11434."
fi
echo "Ollama listo"

# ---- 3. Configuración ------------------------------------------------
if [ ! -f .env ]; then
  paso "Creando .env con contraseñas aleatorias"
  clave() { openssl rand -hex 12; }
  {
    echo "# Generado por Ad Astra.command el $(date '+%Y-%m-%d %H:%M')"
    echo "POSTGRES_PASSWORD=$(clave)"
    echo "ESCUELA_ADMIN_PASSWORD=$(clave)"
    echo "APP_LECTOR_PASSWORD=$(clave)"
    echo "APP_INGESTA_PASSWORD=$(clave)"
    echo "DOCENTE_PASSWORD=$(clave)"
    echo "DIRECCION_PASSWORD=$(clave)"
  } > .env
  chmod 600 .env
fi
# Variables añadidas en versiones posteriores: se completan en un .env antiguo
for v in ADMINISTRACION_PASSWORD SESION_CLAVE; do
  grep -q "^$v=." .env || echo "$v=$(openssl rand -hex 16)" >> .env
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
paso "Arrancando Ad Astra (la primera vez construye la imagen: varios minutos)"
notificar "Preparando la aplicación (la primera vez tarda varios minutos)…"
docker compose up -d --build || fallo "docker compose ha fallado. Revisa los mensajes de arriba."

paso "Esperando a que la aplicación responda"
for _ in $(seq 1 150); do
  curl -sf -o /dev/null "http://localhost:$PUERTO/login.html" && break
  sleep 2
done
curl -sf -o /dev/null "http://localhost:$PUERTO/login.html" \
  || fallo "La aplicación no responde. Mira los logs con: docker compose logs app"

open "http://localhost:$PUERTO"

if [ "$SIN_TERMINAL" = 1 ]; then
  dialogo "Ad Astra está en marcha: http://localhost:$PUERTO

Usuarios (inicia sesión en la web con uno de ellos):
   docente  /  $DOCENTE_PASSWORD
   direccion  /  $DIRECCION_PASSWORD
   administracion  /  $ADMINISTRACION_PASSWORD

La primera vez, los datos de demostración y el asistente se preparan en segundo plano durante unos minutos. Para pararlo, abre «Parar Ad Astra»."
  exit 0
fi

cat <<EOF

$(printf '\033[1;32m✓ Ad Astra está en marcha: http://localhost:%s\033[0m' "$PUERTO")

  Usuarios (inicia sesión en la web con uno de ellos):
    docente         / $DOCENTE_PASSWORD
    direccion       / $DIRECCION_PASSWORD
    administracion  / $ADMINISTRACION_PASSWORD

  La primera vez, los datos de demostración y el asistente se preparan en segundo
  plano durante unos minutos. Para pararlo, abre «Parar Ad Astra.command».

EOF
read -r -p "Pulsa Intro para cerrar esta ventana (la app sigue funcionando)…"
