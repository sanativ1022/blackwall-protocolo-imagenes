param([string]$Imagen, [string[]]$Imagenes, [int]$Puerto = 8080)
$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
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
