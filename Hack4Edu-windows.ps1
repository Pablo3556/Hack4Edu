# ---------------------------------------------------------------------
# Hack4Edu - arranque con un clic (Windows)
#   1. Comprueba Docker Desktop y Ollama.
#   2. Crea .env con contraseñas aleatorias la primera vez.
#   3. Descarga los modelos que falten.
#   4. Arranca el contenedor y abre el navegador.
# Doble clic en Hack4Edu-windows.bat (este script lo invoca).
# ---------------------------------------------------------------------
$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

function Paso($s) { Write-Host "`n> $s" -ForegroundColor Cyan }
function Fallo($s) {
    Write-Host "`nX $s" -ForegroundColor Red
    Read-Host "Pulsa Intro para cerrar"
    exit 1
}
function Clave() {
    -join ((1..24) | ForEach-Object { "{0:x}" -f (Get-Random -Maximum 16) })
}

# ---- 1. Docker --------------------------------------------------------
Paso "Comprobando Docker"
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Fallo "Falta Docker Desktop. Instalalo (https://www.docker.com/products/docker-desktop/) y vuelve a intentarlo."
}
$dockerListo = $false
try { docker info *>$null; $dockerListo = $true } catch { $dockerListo = $false }
if (-not $dockerListo) {
    Write-Host "Abriendo Docker Desktop..."
    Start-Process "Docker Desktop" -ErrorAction SilentlyContinue
    for ($i = 0; $i -lt 90; $i++) {
        try { docker info *>$null; $dockerListo = $true; break } catch { Start-Sleep -Seconds 2 }
    }
    if (-not $dockerListo) { Fallo "Docker Desktop no ha terminado de arrancar. Abrelo a mano y vuelve a intentarlo." }
}
Write-Host "Docker listo"

# ---- 2. Ollama ----------------------------------------------------------
Paso "Comprobando Ollama"
function OllamaOk {
    try { Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:11434/api/tags" -TimeoutSec 2 | Out-Null; return $true }
    catch { return $false }
}
if (-not (OllamaOk)) {
    if (Get-Command ollama -ErrorAction SilentlyContinue) {
        Start-Process -WindowStyle Hidden ollama -ArgumentList "serve"
    } else {
        Fallo "Falta Ollama. Instalalo (https://ollama.com/download) y vuelve a abrir Hack4Edu."
    }
    $ollamaListo = $false
    for ($i = 0; $i -lt 30; $i++) { if (OllamaOk) { $ollamaListo = $true; break }; Start-Sleep -Seconds 1 }
    if (-not $ollamaListo) { Fallo "Ollama no responde en localhost:11434." }
}
Write-Host "Ollama listo"

# ---- 3. Configuracion ----------------------------------------------------
if (-not (Test-Path ".env")) {
    Paso "Creando .env con contrasenas aleatorias"
    @(
        "# Generado por Hack4Edu-windows.ps1 el $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
        "POSTGRES_PASSWORD=$(Clave)"
        "ESCUELA_ADMIN_PASSWORD=$(Clave)"
        "APP_LECTOR_PASSWORD=$(Clave)"
        "APP_INGESTA_PASSWORD=$(Clave)"
        "DOCENTE_PASSWORD=$(Clave)"
        "DIRECCION_PASSWORD=$(Clave)"
        "ADMINISTRACION_PASSWORD=$(Clave)"
        "SESION_CLAVE=$(Clave)$(Clave)"
    ) | Set-Content -Encoding ascii ".env"
}
# Variables anadidas en versiones posteriores: se completan en un .env antiguo
$envTexto = Get-Content ".env" -Raw
foreach ($v in @("ADMINISTRACION_PASSWORD", "SESION_CLAVE")) {
    if ($envTexto -notmatch "(?m)^$v=.+$") {
        Add-Content ".env" "$v=$(Clave)"
    }
}

# Carga las variables de .env en esta sesion
$envVars = @{}
Get-Content ".env" | Where-Object { $_ -match "^[A-Z_]+=" } | ForEach-Object {
    $partes = $_ -split "=", 2
    [System.Environment]::SetEnvironmentVariable($partes[0], $partes[1], "Process")
    $envVars[$partes[0]] = $partes[1]
}
$puerto = if ($envVars["APP_PUERTO"]) { $envVars["APP_PUERTO"] } else { "8080" }

# ---- 4. Modelos -----------------------------------------------------------
Paso "Comprobando modelos (la primera vez tarda unos minutos)"
$chat = if ($envVars["OLLAMA_CHAT_MODEL"]) { $envVars["OLLAMA_CHAT_MODEL"] } else { "llama3.2:3b" }
$emb  = if ($envVars["OLLAMA_EMBEDDING_MODEL"]) { $envVars["OLLAMA_EMBEDDING_MODEL"] } else { "embeddinggemma" }
$instalados = (ollama list) -join "`n"
foreach ($m in @($chat, $emb)) {
    if ($instalados -match [regex]::Escape($m)) {
        Write-Host "OK $m"
    } else {
        ollama pull $m
        if ($LASTEXITCODE -ne 0) { Fallo "No se pudo descargar el modelo $m." }
    }
}

# ---- 5. Aplicacion ----------------------------------------------------------
Paso "Arrancando Hack4Edu (la primera vez construye la imagen: varios minutos)"
docker compose up -d --build
if ($LASTEXITCODE -ne 0) { Fallo "docker compose ha fallado. Revisa los mensajes de arriba." }

Paso "Esperando a que la aplicacion responda"
$listo = $false
for ($i = 0; $i -lt 150; $i++) {
    try { Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:$puerto/login.html" -TimeoutSec 2 | Out-Null; $listo = $true; break }
    catch { Start-Sleep -Seconds 2 }
}
if (-not $listo) { Fallo "La aplicacion no responde. Mira los logs con: docker compose logs app" }

Start-Process "http://localhost:$puerto"

Write-Host "`n`nHack4Edu esta en marcha: http://localhost:$puerto" -ForegroundColor Green
Write-Host "`n  Usuarios (inicia sesion en la web con uno de ellos):"
Write-Host "    docente         / $($envVars['DOCENTE_PASSWORD'])"
Write-Host "    direccion       / $($envVars['DIRECCION_PASSWORD'])"
Write-Host "    administracion  / $($envVars['ADMINISTRACION_PASSWORD'])"
Write-Host "`n  La primera vez, los datos de demostracion y el asistente se preparan en segundo"
Write-Host "  plano durante unos minutos. Para pararlo, ejecuta Parar-Hack4Edu-windows.bat"
Read-Host "`nPulsa Intro para cerrar esta ventana (la app sigue funcionando)"
