param([string]$Imagen, [string[]]$Imagenes, [int]$Puerto = 8080)
$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
if (-not $Imagen -and -not $Imagenes) {
    $archivoImagenes = Join-Path $raiz 'imagenes.local.txt'
    if (-not (Test-Path -LiteralPath $archivoImagenes -PathType Leaf)) {
        throw 'No existe imagenes.local.txt. Copie imagenes.example.txt y coloque las rutas de sus imagenes.'
    }
    $Imagenes = @(Get-Content -LiteralPath $archivoImagenes | ForEach-Object { $_.Trim() } |
        Where-Object { $_ -and -not $_.StartsWith('#') })
    if ($Imagenes.Count -eq 0) { throw 'imagenes.local.txt no contiene imagenes.' }
    foreach ($entrada in $Imagenes) {
        if ($entrada -notmatch '^[A-Za-z0-9_-]+=(.+)$') {
            throw "Entrada invalida en imagenes.local.txt: $entrada"
        }
        if (-not (Test-Path -LiteralPath $Matches[1] -PathType Leaf)) {
            throw "No existe la imagen indicada: $($Matches[1])"
        }
    }
}
& (Join-Path $PSScriptRoot 'Compilar.ps1')
Push-Location $raiz
try {
    if ($Imagenes.Count -gt 0) {
        $argumentosServidor = @('--puerto', "$Puerto")
        foreach ($entrada in $Imagenes) { $argumentosServidor += @('--imagen', $entrada) }
        java -cp 'construccion\clases' blackwall.servidor.IniciarServidor @argumentosServidor
    } elseif ($Imagen) {
        java -cp 'construccion\clases' blackwall.servidor.IniciarServidor $Imagen $Puerto
    } else { throw 'Indique -Imagen o -Imagenes.' }
}
finally { Pop-Location }
