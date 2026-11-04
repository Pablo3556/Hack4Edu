#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Hack4Edu · arranque con doble clic (macOS)
#   1. Comprueba Docker Desktop y Ollama (y los abre si están cerrados).
#   2. Crea .env con contraseñas aleatorias la primera vez.
#   3. Descarga los modelos que falten.
#   4. Arranca el contenedor y abre el navegador.
# ---------------------------------------------------------------------
set -uo pipefail
cd "$(dirname "$0")"
export PATH="/usr/local/bin:/opt/homebrew/bin:/Applications/Docker.app/Contents/Resources/bin:$PATH"

paso()  { printf '\n\033[1;34m▸ %s\033[0m\n' "$*"; }
aviso() { osascript -e "display dialog \"$1\" with title \"Hack4Edu\" buttons {\"OK\"} default button 1 with icon caution" >/dev/null 2>&1; }
fallo() { printf '\n\033[1;31m✗ %s\033[0m\n' "$1"; aviso "$1"; [ -n "${2:-}" ] && open "$2"; read -r -p "Pulsa Intro para cerrar…"; exit 1; }

# ---- 1. Docker -------------------------------------------------------
paso "Comprobando Docker"
command -v docker >/dev/null || fallo "Falta Docker Desktop. Instálalo y vuelve a abrir Hack4Edu." "https://www.docker.com/products/docker-desktop/"
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
  else fallo "Falta Ollama. Instálalo y vuelve a abrir Hack4Edu." "https://ollama.com/download"
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
    echo "# Generado por Hack4Edu.command el $(date '+%Y-%m-%d %H:%M')"
    echo "POSTGRES_PASSWORD=$(clave)"
    echo "ESCUELA_ADMIN_PASSWORD=$(clave)"
    echo "APP_LECTOR_PASSWORD=$(clave)"
    echo "APP_INGESTA_PASSWORD=$(clave)"
    echo "DOCENTE_PASSWORD=$(clave)"
    echo "DIRECCION_PASSWORD=$(clave)"
  } > .env
  chmod 600 .env
fi
set -a; source .env; set +a
PUERTO="${APP_PUERTO:-8080}"

# ---- 4. Modelos ------------------------------------------------------
paso "Comprobando modelos (la primera vez tarda unos minutos)"
for m in "${OLLAMA_CHAT_MODEL:-llama3.2:3b}" "${OLLAMA_EMBEDDING_MODEL:-embeddinggemma}"; do
  if ollama list 2>/dev/null | awk 'NR>1{print $1}' | grep -qx -e "$m" -e "$m:latest"; then
    echo "✓ $m"
  else
    ollama pull "$m" || fallo "No se pudo descargar el modelo $m."
  fi
done

# ---- 5. Aplicación ---------------------------------------------------
paso "Arrancando Hack4Edu (la primera vez construye la imagen: varios minutos)"
docker compose up -d --build || fallo "docker compose ha fallado. Revisa los mensajes de arriba."

paso "Esperando a que la aplicación responda"
for _ in $(seq 1 150); do
  curl -sf -o /dev/null "http://localhost:$PUERTO/api/datos/ambitos" && break
  sleep 2
done
curl -sf -o /dev/null "http://localhost:$PUERTO/api/datos/ambitos" \
  || fallo "La aplicación no responde. Mira los logs con: docker compose logs app"

open "http://localhost:$PUERTO"
cat <<EOF

$(printf '\033[1;32m✓ Hack4Edu está en marcha: http://localhost:%s\033[0m' "$PUERTO")

  Usuario docente:    docente    / $DOCENTE_PASSWORD
  Usuario dirección:  direccion  / $DIRECCION_PASSWORD

  La primera vez, los datos de demostración y el asistente se preparan en segundo
  plano durante unos minutos. Para pararlo, abre «Parar Hack4Edu.command».

EOF
read -r -p "Pulsa Intro para cerrar esta ventana (la app sigue funcionando)…"
