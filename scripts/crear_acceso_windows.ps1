# ---------------------------------------------------------------------
# Crea un acceso directo a Hack4Edu con el icono de Ad Astra en el Escritorio
# y en el menu Inicio.
#   powershell -ExecutionPolicy Bypass -File scripts\crear_acceso_windows.ps1
# El acceso apunta a esta carpeta: no la muevas despues de crearlo.
# ---------------------------------------------------------------------
$ErrorActionPreference = "Stop"
$raiz  = Split-Path -Parent $PSScriptRoot
$icono = Join-Path $raiz "Iconos_AdAstra\windows\AdAstra.ico"
$bat   = Join-Path $raiz "Hack4Edu-windows.bat"
$shell = New-Object -ComObject WScript.Shell

$destinos = @([Environment]::GetFolderPath("Desktop"), [Environment]::GetFolderPath("Programs"))
foreach ($carpeta in $destinos) {
    $acceso = $shell.CreateShortcut((Join-Path $carpeta "Hack4Edu.lnk"))
    $acceso.TargetPath       = $bat
    $acceso.WorkingDirectory = $raiz
    $acceso.IconLocation     = "$icono,0"
    $acceso.Description      = "Ad Astra - La escuela que aprende de sus datos"
    $acceso.Save()
}
Write-Host "Acceso directo creado en el Escritorio y en el menu Inicio."
