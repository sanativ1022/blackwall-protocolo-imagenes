$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
$salida = Join-Path $raiz 'construccion'
if ((Split-Path -Parent $salida) -ne $raiz) { throw 'Ruta de limpieza fuera del proyecto.' }
if (Test-Path -LiteralPath $salida) {
    Remove-Item -LiteralPath $salida -Recurse -Force
}
Write-Host '[OK] Archivos compilados eliminados. Las imagenes y documentos se conservan.' -ForegroundColor Green
