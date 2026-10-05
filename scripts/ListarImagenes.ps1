$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$archivo = Join-Path $raiz 'imagenes.local.txt'
if (-not (Test-Path -LiteralPath $archivo -PathType Leaf)) {
    throw 'No existe imagenes.local.txt. Copie imagenes.example.txt o use make add-image.'
}
$entradas = @(Get-Content -LiteralPath $archivo | ForEach-Object { $_.Trim() } |
    Where-Object { $_ -and -not $_.StartsWith('#') })
if ($entradas.Count -eq 0) { Write-Host 'No hay imagenes registradas.'; exit 0 }
foreach ($entrada in $entradas) {
    $partes = $entrada -split '=', 2
    if ($partes.Count -ne 2) { Write-Warning "Entrada invalida: $entrada"; continue }
    $estado = if (Test-Path -LiteralPath $partes[1] -PathType Leaf) { 'OK' } else { 'NO EXISTE' }
    Write-Host "[$estado] $($partes[0]): $($partes[1])"
}
