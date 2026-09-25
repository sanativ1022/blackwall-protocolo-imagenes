import { CacheMosaicos } from './cache-mosaicos.mjs';

const canvas = document.querySelector('#visor');
const nivelActual = document.querySelector('#nivelActual');
const contexto = canvas.getContext('2d');
const formulario = valores => new URLSearchParams(valores).toString();
const cache = new CacheMosaicos();
let fondoGeneral = null;
let fondoGeneralFinal = false;
const recibidas = new Set();
const movimientos = [];
let sesion, imagen, nivel = 0, vista = 0;
let region = { x: 0, y: 0, ancho: 1, alto: 1 };
let arrastre = null, temporizador = null, solicitando = false, nuevaVistaPendiente = false;
let movimientoPendiente = { direccionX: 0, direccionY: 0, velocidadX: 0, velocidadY: 0, estabilidad: 0 };
let ack = 0;
let vistaConcluida = 0;
let mosaicosVista = 0;

async function pedirTexto(url, opciones = {}) {
  const respuesta = await fetch(url, opciones);
  if (!respuesta.ok) throw Error(await respuesta.text());
  return respuesta.text();
}

async function iniciar() {
  ajustarCanvas();
  sesion = (await pedirTexto('/protocolo/sesiones', { method: 'POST' })).split('=')[1];
  const linea = (await pedirTexto('/protocolo/imagenes')).trim().split('\n')[0].split('|');
  imagen = { id: linea[0], ancho: Number(linea[1]), alto: Number(linea[2]), maximo: Number(linea[3]) };
  nivel = 0;
  mostrarNivel();
  encuadrar(); configurarEventos();
  await solicitarVista();
  ciclo();
}

function ajustarCanvas() {
  const escala = devicePixelRatio || 1;
  canvas.width = Math.round(canvas.clientWidth * escala);
  canvas.height = Math.round(canvas.clientHeight * escala);
  contexto.setTransform(escala, 0, 0, escala, 0, 0);
}

function encuadrar() {
  const proporcion = canvas.clientWidth / canvas.clientHeight;
  region.ancho = Math.min(imagen.ancho, Math.ceil(imagen.alto * proporcion));
  region.alto = Math.min(imagen.alto, Math.ceil(region.ancho / proporcion));
  region.x = (imagen.ancho - region.ancho) / 2;
  region.y = (imagen.alto - region.alto) / 2;
}

function mostrarNivel() {
  nivelActual.textContent = `Nivel ${nivel}/${imagen.maximo}`;
}

function configurarEventos() {
  addEventListener('resize', () => { ajustarCanvas(); solicitarPronto(); });
  canvas.addEventListener('wheel', evento => {
    evento.preventDefault();
    nivel = Math.max(0, Math.min(imagen.maximo, nivel + (evento.deltaY < 0 ? 1 : -1)));
    mostrarNivel();
    const factor = evento.deltaY < 0 ? 0.55 : 1.8;
    const centroX = region.x + region.ancho * evento.offsetX / canvas.clientWidth;
    const centroY = region.y + region.alto * evento.offsetY / canvas.clientHeight;
    region.ancho = Math.max(64, Math.min(imagen.ancho, region.ancho * factor));
    region.alto = Math.max(64, Math.min(imagen.alto, region.alto * factor));
    region.x = centroX - region.ancho * evento.offsetX / canvas.clientWidth;
    region.y = centroY - region.alto * evento.offsetY / canvas.clientHeight;
    limitarRegion(); solicitarPronto();
  }, { passive: false });
  canvas.addEventListener('pointerdown', evento => {
    arrastre = { x: evento.clientX, y: evento.clientY, tiempo: performance.now() };
    movimientos.length = 0;
    canvas.setPointerCapture(evento.pointerId);
  });
  canvas.addEventListener('pointermove', evento => {
    if (!arrastre) return;
    const ahora = performance.now();
    const dx = (arrastre.x - evento.clientX) * region.ancho / canvas.clientWidth;
    const dy = (arrastre.y - evento.clientY) * region.alto / canvas.clientHeight;
    const segundos = Math.max(0.005, (ahora - arrastre.tiempo) / 1000);
    movimientos.push({ vx: dx / segundos, vy: dy / segundos });
    if (movimientos.length > 5) movimientos.shift();
    region.x += dx; region.y += dy;
    arrastre = { x: evento.clientX, y: evento.clientY, tiempo: ahora };
    limitarRegion(); dibujar();
  });
  canvas.addEventListener('pointerup', () => {
    arrastre = null;
    movimientoPendiente = resumenMovimiento();
    solicitarPronto(movimientoPendiente);
  });
}

function resumenMovimiento() {
  if (movimientos.length < 3) return { direccionX: 0, direccionY: 0, velocidadX: 0, velocidadY: 0, estabilidad: 0 };
  const vx = movimientos.reduce((s, m) => s + m.vx, 0) / movimientos.length;
  const vy = movimientos.reduce((s, m) => s + m.vy, 0) / movimientos.length;
  const eje = Math.abs(vx) >= Math.abs(vy) ? 'vx' : 'vy';
  const signo = Math.sign(eje === 'vx' ? vx : vy);
  const estabilidad = movimientos.filter(m => Math.sign(m[eje]) === signo).length / movimientos.length;
  return { direccionX: Math.sign(vx), direccionY: Math.sign(vy),
    velocidadX: Math.abs(vx), velocidadY: Math.abs(vy), estabilidad };
}

function limitarRegion() {
  region.x = Math.max(0, Math.min(imagen.ancho - region.ancho, region.x));
  region.y = Math.max(0, Math.min(imagen.alto - region.alto, region.y));
}

function solicitarPronto(movimiento = { direccionX: 0, direccionY: 0, velocidadX: 0, velocidadY: 0, estabilidad: 0 }) {
  movimientoPendiente = movimiento;
  clearTimeout(temporizador);
  temporizador = setTimeout(() => solicitarVista().catch(error => mostrarError(error)), 120);
}

async function solicitarVista() {
  if (solicitando) { nuevaVistaPendiente = true; return; }
  solicitando = true;
  try {
    const proxima = vista + 1;
    const parametros = formulario({ sesion, imagen: imagen.id, vista: proxima,
      x: Math.floor(region.x), y: Math.floor(region.y), ancho: Math.max(1, Math.floor(region.ancho)),
      alto: Math.max(1, Math.floor(region.alto)), nivel, ventana: 16,
      ...movimientoPendiente, cache: [...cache.keys()].join(',') });
    const aceptacion = new URLSearchParams(await pedirTexto('/protocolo/vistas', { method: 'POST', body: parametros }));
    mosaicosVista = Number(aceptacion.get('mosaicos'));
    vista = proxima; ack = 0; recibidas.clear();
    dibujar();
    if (mosaicosVista === 0) {
      // La vista ya esta completa en el cliente: no pedir un lote vacio.
      vistaConcluida = vista;
    }
  } finally {
    solicitando = false;
    if (nuevaVistaPendiente) { nuevaVistaPendiente = false; await solicitarVista(); }
  }
}

async function ciclo() {
  let demoraReintento = 500;
  while (true) {
    let demora = 80;
    try {
      if (!solicitando && vista > 0 && vista !== vistaConcluida) {
        const vistaSolicitada = vista;
        const sack = [...recibidas].filter(n => n > ack).sort((a, b) => a - b).map(n => `${n}-${n}`).join(',');
        const respuesta = await fetch('/protocolo/lotes', { method: 'POST', body: formulario({
          sesion, vista: vistaSolicitada, ack, sack, ventana: 16, demoraAck: 0 }) });
        if (!respuesta.ok) throw Error(await respuesta.text());
        await procesarLote(await respuesta.arrayBuffer(), vistaSolicitada);
        const estado = await actualizarEstado(respuesta.headers.get('X-Estado-Protocolo'), vistaSolicitada);
        if (vistaSolicitada === vista && estado && estado.pendientes === 0 && estado.enVuelo === 0) {
          vistaConcluida = vistaSolicitada;
        }
        demoraReintento = 500;
      }
    } catch (error) {
      if (!solicitando) mostrarError(error);
      demora = demoraReintento;
      demoraReintento = Math.min(demoraReintento * 2, 8000);
    }
    await new Promise(resolver => setTimeout(resolver, demora));
  }
}

async function procesarLote(buffer, vistaSolicitada) {
  if (vistaSolicitada !== vista) return;
  const datos = new DataView(buffer);
  let posicion = 0;
  const cantidad = datos.getInt32(posicion); posicion += 4;
  for (let i = 0; i < cantidad; i++) {
    const largo = datos.getInt32(posicion); posicion += 4;
    const fin = posicion + largo;
    if (datos.getUint32(posicion) !== 0x42574931) throw Error('Trama incompatible');
    posicion += 6;
    const secuencia = Number(datos.getBigInt64(posicion)); posicion += 8;
    const vistaDato = Number(datos.getBigInt64(posicion)); posicion += 8;
    const largoId = datos.getUint16(posicion); posicion += 2;
    const largoContenido = datos.getInt32(posicion); posicion += 4;
    const crcEsperado = datos.getUint32(posicion); posicion += 4;
    const id = new TextDecoder().decode(new Uint8Array(buffer, posicion, largoId)); posicion += largoId;
    const contenido = new Uint8Array(buffer, posicion, largoContenido); posicion = fin;
    if (vistaDato !== vista || crc32(contenido) !== crcEsperado) continue;
    const tipo = id.endsWith(':F') ? 'image/png' : 'image/jpeg';
    const url = URL.createObjectURL(new Blob([contenido], { type: tipo }));
    const mosaico = new Image();
    try {
      await new Promise((resolver, fallar) => { mosaico.onload = resolver; mosaico.onerror = fallar; mosaico.src = url; });
    } finally { URL.revokeObjectURL(url); }
    if (vistaDato !== vista) continue;
    if (id === `${imagen.id}:0:0:0:F`) { fondoGeneral = mosaico; fondoGeneralFinal = true; }
    else if (id === `${imagen.id}:0:0:0:P` && !fondoGeneralFinal) fondoGeneral = mosaico;
    cache.set(id, mosaico, contenido.byteLength);
    recibidas.add(secuencia);
    while (recibidas.has(ack + 1)) ack++;
  }
  dibujar();
}

function crc32(bytes) {
  let crc = 0xffffffff;
  for (const valor of bytes) {
    crc ^= valor;
    for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function dibujar() {
  contexto.clearRect(0, 0, canvas.clientWidth, canvas.clientHeight);
  if (fondoGeneral && nivel > 0) contexto.drawImage(fondoGeneral,
    region.x * fondoGeneral.width / imagen.ancho,
    region.y * fondoGeneral.height / imagen.alto,
    region.ancho * fondoGeneral.width / imagen.ancho,
    region.alto * fondoGeneral.height / imagen.alto,
    0, 0, canvas.clientWidth, canvas.clientHeight);
  // Mantener una imagen de contexto mientras llegan mosaicos mas detallados.
  const disponibles = [...cache].filter(([id]) => {
    const partes = id.split(':');
    return partes[0] === imagen.id && Number(partes[1]) <= nivel;
  }).sort(([idA], [idB]) => {
    const a = idA.split(':'), b = idB.split(':');
    return Number(a[1]) - Number(b[1]) || Number(a[4] === 'F') - Number(b[4] === 'F');
  });
  for (const [id, mosaico] of disponibles) {
    const partes = id.split(':');
    const lado = 256 * 2 ** (imagen.maximo - Number(partes[1]));
    const x = Number(partes[2]) * lado, y = Number(partes[3]) * lado;
    if (x + lado < region.x || y + lado < region.y || x > region.x + region.ancho || y > region.y + region.alto) continue;
    const ancho = Math.min(lado, imagen.ancho - x), alto = Math.min(lado, imagen.alto - y);
    contexto.drawImage(mosaico, (x - region.x) * canvas.clientWidth / region.ancho,
      (y - region.y) * canvas.clientHeight / region.alto,
      ancho * canvas.clientWidth / region.ancho, alto * canvas.clientHeight / region.alto);
    cache.get(id); // El mosaico dibujado gana frecuencia en GDSF.
  }
}

async function actualizarEstado(incluido = null, vistaSolicitada = vista) {
  const datos = new URLSearchParams(incluido ?? await pedirTexto('/protocolo/estado?sesion=' + sesion));
  if (vistaSolicitada !== vista || Number(datos.get('vista')) !== vista) return null;
  return { pendientes: Number(datos.get('pendientes')), enVuelo: Number(datos.get('enVuelo')) };
}

function mostrarError(error) { console.error(error); }
iniciar().catch(mostrarError);
