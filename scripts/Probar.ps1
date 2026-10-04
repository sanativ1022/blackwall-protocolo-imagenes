$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$pruebas = 'construccion\pruebas'
Push-Location $raiz
try {
New-Item -ItemType Directory -Force -Path $pruebas | Out-Null
$fuentesPrincipal = Get-ChildItem -LiteralPath 'src\principal\java' -Recurse -Filter '*.java' | ForEach-Object FullName
$fuentesPruebas = Get-ChildItem -LiteralPath 'src\pruebas\java' -Recurse -Filter '*.java' | ForEach-Object FullName
javac --release 21 -encoding UTF-8 -d $pruebas $fuentesPrincipal $fuentesPruebas
if ($LASTEXITCODE -ne 0) { throw 'La compilacion de pruebas fallo.' }
java -ea -cp $pruebas blackwall.pruebas.PruebasNucleoProtocolo
if ($LASTEXITCODE -ne 0) { throw 'Las pruebas fallaron.' }
java -ea -cp $pruebas blackwall.pruebas.PruebasImagenRegional
if ($LASTEXITCODE -ne 0) { throw 'Las pruebas regionales fallaron.' }
java -ea -cp $pruebas blackwall.pruebas.PruebasIntegracionServidor
if ($LASTEXITCODE -ne 0) { throw 'Las pruebas de integracion fallaron.' }
java -ea -cp $pruebas blackwall.pruebas.PruebasLimpiezaSesiones
if ($LASTEXITCODE -ne 0) { throw 'La prueba de limpieza de sesiones fallo.' }
java -ea -cp $pruebas blackwall.pruebas.PruebasPoliticasProtocolo
if ($LASTEXITCODE -ne 0) { throw 'Las pruebas de politicas fallaron.' }
java -ea -cp $pruebas blackwall.pruebas.PruebasAdaptacionFlare
if ($LASTEXITCODE -ne 0) { throw 'Las pruebas de adaptacion Flare fallaron.' }
node 'scripts\ProbarCacheCliente.mjs'
if ($LASTEXITCODE -ne 0) { throw 'Las pruebas de cache cliente fallaron.' }
node 'scripts\ProbarNavegacionZoom.mjs'
if ($LASTEXITCODE -ne 0) { throw 'Las pruebas de navegacion y zoom fallaron.' }
node 'scripts\ProbarRecuperacionSesion.mjs'
if ($LASTEXITCODE -ne 0) { throw 'Las pruebas de recuperacion de sesion fallaron.' }
} finally { Pop-Location }
