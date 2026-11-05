# ---------------------------------------------------------------------
# Crea accesos directos para arrancar y parar Ad Astra con su icono en el Escritorio
# y en el menu Inicio.
#   powershell -ExecutionPolicy Bypass -File scripts\crear_acceso_windows.ps1
# El acceso apunta a esta carpeta: no la muevas despues de crearlo.
# ---------------------------------------------------------------------
$ErrorActionPreference = "Stop"
$raiz  = Split-Path -Parent $PSScriptRoot
$icono = Join-Path $raiz "Iconos_AdAstra\windows\AdAstra.ico"
# El .vbs lanza AdAstra-windows.ps1 sin ventana de consola
$vbs   = Join-Path $raiz "scripts\arranque_oculto_windows.vbs"
$shell = New-Object -ComObject WScript.Shell

$vbsParar = Join-Path $raiz "scripts\parar_oculto_windows.vbs"

$destinos = @([Environment]::GetFolderPath("Desktop"), [Environment]::GetFolderPath("Programs"))
foreach ($carpeta in $destinos) {
    $acceso = $shell.CreateShortcut((Join-Path $carpeta "Ad Astra.lnk"))
    $acceso.TargetPath       = Join-Path $env:WINDIR "System32\wscript.exe"
    $acceso.Arguments        = "`"$vbs`""
    $acceso.WorkingDirectory = $raiz
    $acceso.IconLocation     = "$icono,0"
    $acceso.Description      = "Ad Astra - La escuela que aprende de sus datos"
    $acceso.Save()

    $parar = $shell.CreateShortcut((Join-Path $carpeta "Parar Ad Astra.lnk"))
    $parar.TargetPath       = Join-Path $env:WINDIR "System32\wscript.exe"
    $parar.Arguments        = "`"$vbsParar`""
    $parar.WorkingDirectory = $raiz
    $parar.IconLocation     = "$icono,0"
    $parar.Description      = "Para Ad Astra (los datos se conservan)"
    $parar.Save()
}
Write-Host "Accesos directos (Ad Astra y Parar Ad Astra) creados en el Escritorio y en el menu Inicio."
