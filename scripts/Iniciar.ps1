param([Parameter(Mandatory=$true)][string]$Imagen, [int]$Puerto = 8080)
$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'Compilar.ps1')
Push-Location $raiz
try { java -cp 'construccion\clases' blackwall.servidor.IniciarServidor $Imagen $Puerto }
finally { Pop-Location }
