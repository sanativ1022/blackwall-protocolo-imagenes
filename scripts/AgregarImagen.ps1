param(
    [string]$Id,
    [string]$Ruta,
    [string]$ArchivoConfiguracion
)
$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
if (-not $ArchivoConfiguracion) { $ArchivoConfiguracion = Join-Path $raiz 'imagenes.local.txt' }
if ($Id -notmatch '^[A-Za-z0-9_-]+$') {
    throw 'Indique ID con letras, numeros, guion o guion bajo.'
}
if (-not $Ruta) { throw 'Indique FILE con la ruta de la imagen.' }
$rutaCompleta = (Resolve-Path -LiteralPath $Ruta -ErrorAction Stop).Path
if (-not (Test-Path -LiteralPath $rutaCompleta -PathType Leaf)) {
    throw "No existe el archivo de imagen: $Ruta"
}
$entradas = @()
if (Test-Path -LiteralPath $ArchivoConfiguracion -PathType Leaf) {
    $entradas = @(Get-Content -LiteralPath $ArchivoConfiguracion)
}
foreach ($entrada in $entradas) {
    if ($entrada.Trim() -match '^([^#=][^=]*)=') {
        if ($Matches[1] -eq $Id) { throw "Ya existe una imagen con ID $Id. Elija otro ID." }
    }
}
Add-Content -LiteralPath $ArchivoConfiguracion -Value "$Id=$rutaCompleta" -Encoding UTF8
Write-Host "[OK] Imagen $Id registrada. Reinicie el servidor con make server para verla." -ForegroundColor Green
