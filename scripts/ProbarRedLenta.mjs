import assert from 'node:assert/strict';
import { setTimeout as esperar } from 'node:timers/promises';

const base = process.argv[2] ?? 'http://127.0.0.1:8083';
const kbps = Number(process.argv[3] ?? 400);
const latenciaMs = Number(process.argv[4] ?? 2000);
if (!Number.isFinite(kbps) || kbps <= 0 || !Number.isFinite(latenciaMs) || latenciaMs < 0)
  throw Error('Uso: node ProbarRedLenta.mjs URL kbps latenciaMs');

let peticiones = 0;
async function solicitar(ruta, datos) {
  await esperar(latenciaMs);
  peticiones++;
  const respuesta = await fetch(base + ruta, datos ? { method: 'POST', body: new URLSearchParams(datos) } : {});
  if (!respuesta.ok) throw Error(`${ruta}: ${respuesta.status} ${await respuesta.text()}`);
  return respuesta;
}

async function consumirLento(respuesta) {
  const partes = [];
  let bytes = 0;
  for await (const trozo of respuesta.body) {
    for (let inicio = 0; inicio < trozo.length; inicio += 1024) {
      const parte = trozo.subarray(inicio, Math.min(inicio + 1024, trozo.length));
      await esperar(parte.length * 8 * 1000 / (kbps * 1000));
      partes.push(Buffer.from(parte)); bytes += parte.length;
    }
  }
  return { datos: Buffer.concat(partes), bytes };
}

function secuencias(datos) {
  const cantidad = datos.readInt32BE(0);
  const salida = [];
  let posicion = 4;
  for (let i = 0; i < cantidad; i++) {
    const largo = datos.readInt32BE(posicion); posicion += 4;
    assert.equal(datos.readUInt32BE(posicion), 0x42574931);
    salida.push(Number(datos.readBigInt64BE(posicion + 6)));
    posicion += largo;
  }
  assert.equal(posicion, datos.length);
  return salida;
}

const sesion = new URLSearchParams(await (await solicitar('/protocolo/sesiones', {})).text()).get('sesion');
const [imagen, , , nivel] = (await (await solicitar('/protocolo/imagenes')).text()).trim().split('|');
const peticionesInicializacion = peticiones;
const inicio = performance.now();
const aceptacion = new URLSearchParams(await (await solicitar('/protocolo/vistas', {
  sesion, imagen, vista: 1, x: 53760, y: 40448, ancho: 256, alto: 256, nivel, ventana: 16
})).text());
assert.equal(aceptacion.get('mosaicos'), '2', 'se esperaba una previa y un final');

let ack = 0, bytesImagen = 0, previaMs, finalMs, terminado = false;
for (let vuelta = 0; vuelta < 10; vuelta++) {
  const respuesta = await solicitar('/protocolo/lotes', { sesion, vista: 1, ack, ventana: 16 });
  const estado = new URLSearchParams(respuesta.headers.get('X-Estado-Protocolo'));
  const lote = await consumirLento(respuesta);
  bytesImagen += lote.bytes;
  for (const secuencia of secuencias(lote.datos)) {
    assert.equal(secuencia, ack + 1);
    ack = secuencia;
    if (ack === 1) previaMs = Math.round(performance.now() - inicio);
    if (ack === 2) finalMs = Math.round(performance.now() - inicio);
  }
  if (estado.get('pendientes') === '0' && estado.get('enVuelo') === '0') {
    terminado = true; break;
  }
}
assert.ok(terminado && ack === 2, 'la vista debe completarse aun con consumo lento');
const idBase = `${imagen}:${nivel}:${53760 / 256}:${40448 / 256}`;
const revisita = new URLSearchParams(await (await solicitar('/protocolo/vistas', {
  sesion, imagen, vista: 2, x: 53760, y: 40448, ancho: 256, alto: 256, nivel, ventana: 16,
  cache: `${idBase}:P,${idBase}:F`
})).text());
assert.equal(revisita.get('mosaicos'), '0', 'la revisita cacheada no requiere datos');
console.log(JSON.stringify({ kbps, latenciaMs, mosaicos: ack, previaMs, finalMs,
  bytesImagen, peticionesInicializacion, peticionesPrimeraVista: peticiones - 1 - peticionesInicializacion,
  peticionesRevisita: 1,
  mosaicosRevisita: 0, concluida: terminado }, null, 2));
