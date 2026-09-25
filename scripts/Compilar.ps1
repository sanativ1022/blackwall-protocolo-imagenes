$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$salida = 'construccion\clases'
Push-Location $raiz
try {
New-Item -ItemType Directory -Force -Path $salida | Out-Null
$fuentes = Get-ChildItem -LiteralPath 'src\principal\java' -Recurse -Filter '*.java' | ForEach-Object FullName
if (-not $fuentes) { throw 'No se encontraron fuentes Java.' }
javac --release 21 -encoding UTF-8 -d $salida $fuentes
if ($LASTEXITCODE -ne 0) { throw 'La compilacion fallo.' }
Write-Host '[OK] Nucleo compilado.' -ForegroundColor Green
} finally { Pop-Location }
