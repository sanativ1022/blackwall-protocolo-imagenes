import { CacheMosaicos } from './cache-mosaicos.mjs';
import { cambiarZoom } from './navegacion-zoom.mjs';
import { esSesionDesconocida, crearRenovadorSesion } from './recuperacion-sesion.mjs';

const canvas = document.querySelector('#visor');
const nivelActual = document.querySelector('#nivelActual');
const estadoCarga = document.querySelector('#estadoCarga');
const selectorImagen = document.querySelector('#selectorImagen');
const acercar = document.querySelector('#acercar');
const alejar = document.querySelector('#alejar');
const vistaGeneral = document.querySelector('#vistaGeneral');
const zoomNivel = document.querySelector('#zoomNivel');
const pantallaCompleta = document.querySelector('#pantallaCompleta');
const ayudaNavegacion = document.querySelector('#ayudaNavegacion');
const contexto = canvas.getContext('2d');
const formulario = valores => new URLSearchParams(valores).toString();
const cache = new CacheMosaicos();
let fondoGeneral = null;
let fondoGeneralFinal = false;
let ultimoCuadroCompleto = null;
const recibidas = new Set();
const movimientos = [];
const historialZoom = [];
let sesion, imagen, nivel = 0, zoomExtra = 0, vista = 0;
let imagenes = [];
let generacionImagen = 0;
let region = { x: 0, y: 0, ancho: 1, alto: 1 };
let arrastre = null, temporizador = null, solicitando = false, nuevaVistaPendiente = false;
let movimientoPendiente = { direccionX: 0, direccionY: 0, velocidadX: 0, velocidadY: 0, estabilidad: 0 };
let ack = 0;
let vistaConcluida = 0;
let mosaicosVista = 0;
const renovarSesion = crearRenovadorSesion(
  async () => new URLSearchParams(await pedirTexto('/protocolo/sesiones', { method: 'POST' })).get('sesion'),
  () => sesion,
  nueva => { sesion = nueva; ack = 0; recibidas.clear(); vistaConcluida = 0; }
);

async function pedirTexto(url, opciones = {}) {
  const respuesta = await fetch(url, opciones);
  if (!respuesta.ok) throw Error(await respuesta.text());
  return respuesta.text();
}

async function iniciar() {
  ajustarCanvas();
  sesion = (await pedirTexto('/protocolo/sesiones', { method: 'POST' })).split('=')[1];
  imagenes = (await pedirTexto('/protocolo/imagenes')).trim().split('\n').filter(Boolean).map(linea => {
    const [id, ancho, alto, maximo, formato] = linea.split('|');
    return { id, ancho: Number(ancho), alto: Number(alto), maximo: Number(maximo), formato };
  });
  if (!imagenes.length) throw Error('No hay imágenes registradas');
  for (const entrada of imagenes) {
    const opcion = document.createElement('option');
    opcion.value = entrada.id;
    opcion.textContent = entrada.id;
    selectorImagen.append(opcion);
  }
  imagen = imagenes[0];
  nivel = 0;
  mostrarNivel();
  mostrarCarga();
  encuadrar(); configurarEventos();
  try { await solicitarVista(); }
  catch (error) { mostrarError(error); programarVista(1000, generacionImagen); }
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
  nivelActual.textContent = `Nivel ${nivel}/${imagen.maximo}${zoomExtra ? ` · Acercamiento ${zoomExtra}/3` : ''}`;
  zoomNivel.max = imagen.maximo + (imagen.formato === 'PNG' ? 3 : 0);
  zoomNivel.value = nivel + zoomExtra;
  zoomNivel.disabled = false;
  acercar.disabled = nivel === imagen.maximo && (imagen.formato !== 'PNG' || zoomExtra === 3);
  alejar.disabled = nivel === 0 && zoomExtra === 0;
  vistaGeneral.disabled = false;
}

function aplicarZoom(direccion, pivoteX = 0.5, pivoteY = 0.5) {
  const resultado = cambiarZoom({ nivel, zoomExtra, region, direccion, pivoteX, pivoteY,
    imagen, historial: historialZoom });
  if (resultado.nivel === nivel && resultado.zoomExtra === zoomExtra) return;
  nivel = resultado.nivel;
  zoomExtra = resultado.zoomExtra;
  region = resultado.region;
  ayudaNavegacion.hidden = true;
  mostrarNivel();
  dibujar();
  solicitarPronto();
}

function volverAVistaGeneral() {
  nivel = 0;
  zoomExtra = 0;
  historialZoom.length = 0;
  encuadrar();
  ayudaNavegacion.hidden = true;
  mostrarNivel();
  dibujar();
  solicitarPronto();
}

function mostrarCarga() {
  estadoCarga.textContent = `Preparando ${imagen.id}…`;
  estadoCarga.hidden = false;
}

function configurarEventos() {
  acercar.addEventListener('click', () => aplicarZoom(1));
  alejar.addEventListener('click', () => aplicarZoom(-1));
  zoomNivel.addEventListener('input', () => {
    const destino = Number(zoomNivel.value);
    const actual = nivel + zoomExtra;
    const direccion = Math.sign(destino - actual);
    for (let paso = actual; paso !== destino; paso += direccion) aplicarZoom(direccion);
  });
  vistaGeneral.addEventListener('click', volverAVistaGeneral);
  pantallaCompleta.addEventListener('click', alternarPantallaCompleta);
  document.addEventListener('fullscreenchange', () => {
    const activa = document.fullscreenElement === document.querySelector('.visor');
    pantallaCompleta.setAttribute('aria-label', activa ? 'Salir de pantalla completa' : 'Activar pantalla completa');
    pantallaCompleta.title = activa ? 'Salir de pantalla completa (F)' : 'Pantalla completa (F)';
    requestAnimationFrame(() => {
      ajustarCanvas();
      if (imagen) { dibujar(); solicitarPronto(); }
    });
  });
  addEventListener('keydown', evento => {
    if (evento.altKey || evento.ctrlKey || evento.metaKey ||
        ['INPUT', 'SELECT', 'TEXTAREA'].includes(document.activeElement?.tagName)) return;
    if (evento.key === '+' || evento.key === '=') aplicarZoom(1);
    else if (evento.key === '-') aplicarZoom(-1);
    else if (evento.key === '0') volverAVistaGeneral();
    else if (evento.key.toLowerCase() === 'f') alternarPantallaCompleta();
    else return;
    evento.preventDefault();
  });
  selectorImagen.addEventListener('change', () => {
    const elegida = imagenes.find(entrada => entrada.id === selectorImagen.value);
    if (!elegida || elegida.id === imagen.id) return;
    imagen = elegida;
    generacionImagen++;
    nivel = 0;
    zoomExtra = 0;
    historialZoom.length = 0;
    movimientos.length = 0;
    arrastre = null;
    fondoGeneral = null;
    fondoGeneralFinal = false;
    ultimoCuadroCompleto = null;
    vistaConcluida = vista;
    encuadrar();
    mostrarNivel();
    mostrarCarga();
    dibujar();
    solicitarPronto();
  });
  addEventListener('resize', () => {
    historialZoom.length = 0;
    ajustarCanvas();
    dibujar();
    solicitarPronto();
  });
  canvas.addEventListener('wheel', evento => {
    evento.preventDefault();
    aplicarZoom(evento.deltaY < 0 ? 1 : -1,
      evento.offsetX / canvas.clientWidth, evento.offsetY / canvas.clientHeight);
  }, { passive: false });
  canvas.addEventListener('pointerdown', evento => {
    ayudaNavegacion.hidden = true;
    arrastre = { x: evento.clientX, y: evento.clientY, tiempo: performance.now() };
    movimientos.length = 0;
    canvas.setPointerCapture(evento.pointerId);
  });
  canvas.addEventListener('pointermove', evento => {
    if (!arrastre) return;
    const ahora = performance.now();
    const dx = (arrastre.x - evento.clientX) * region.ancho / canvas.clientWidth;
    const dy = (arrastre.y - evento.clientY) * region.alto / canvas.clientHeight;
    if (dx !== 0 || dy !== 0) historialZoom.length = 0;
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

async function alternarPantallaCompleta() {
  try {
    if (document.fullscreenElement) await document.exitFullscreen();
    else await document.querySelector('.visor').requestFullscreen();
  } catch (error) { mostrarError(error); }
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
  programarVista(120, generacionImagen);
}

function programarVista(demora, generacion) {
  clearTimeout(temporizador);
  temporizador = setTimeout(async () => {
    if (generacion !== generacionImagen) return;
    try { await solicitarVista(); }
    catch (error) {
      if (generacion !== generacionImagen) return;
      mostrarError(error);
      programarVista(Math.min(demora * 2, 8000), generacion);
    }
  }, demora);
}

async function solicitarVista() {
  if (solicitando) { nuevaVistaPendiente = true; return; }
  solicitando = true;
  try {
    for (let intento = 0; intento < 2; intento++) {
      const sesionSolicitada = sesion;
      const proxima = vista + 1;
      const parametros = formulario({ sesion: sesionSolicitada, imagen: imagen.id, vista: proxima,
        x: Math.floor(region.x), y: Math.floor(region.y), ancho: Math.max(1, Math.floor(region.ancho)),
        alto: Math.max(1, Math.floor(region.alto)), nivel, ventana: 16,
        ...movimientoPendiente, cache: [...cache.keys()].join(',') });
      let aceptacion;
      try {
        aceptacion = new URLSearchParams(await pedirTexto('/protocolo/vistas', { method: 'POST', body: parametros }));
      } catch (error) {
        if (intento === 0 && esSesionDesconocida(error)) {
          await renovarSesion(sesionSolicitada);
          continue;
        }
        throw error;
      }
      if (sesionSolicitada !== sesion) {
        nuevaVistaPendiente = true;
        return;
      }
      mosaicosVista = Number(aceptacion.get('mosaicos'));
      vista = proxima; ack = 0; recibidas.clear();
      dibujar();
      if (mosaicosVista === 0) {
        // La vista ya esta completa en el cliente: no pedir un lote vacio.
        vistaConcluida = vista;
      }
      return;
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
    let sesionLote = null;
    try {
      if (!solicitando && vista > 0 && vista !== vistaConcluida) {
        const vistaSolicitada = vista;
        const generacionSolicitada = generacionImagen;
        sesionLote = sesion;
        const sack = [...recibidas].filter(n => n > ack).sort((a, b) => a - b).map(n => `${n}-${n}`).join(',');
        const respuesta = await fetch('/protocolo/lotes', { method: 'POST', body: formulario({
          sesion: sesionLote, vista: vistaSolicitada, ack, sack, ventana: 16, demoraAck: 0 }) });
        if (sesionLote !== sesion) continue;
        if (!respuesta.ok) throw Error(await respuesta.text());
        await procesarLote(await respuesta.arrayBuffer(), vistaSolicitada, generacionSolicitada);
        const estado = await actualizarEstado(respuesta.headers.get('X-Estado-Protocolo'), vistaSolicitada);
        if (vistaSolicitada === vista && estado && estado.pendientes === 0 && estado.enVuelo === 0) {
          vistaConcluida = vistaSolicitada;
        }
        demoraReintento = 500;
      }
    } catch (error) {
      if (esSesionDesconocida(error)) {
        try {
          await renovarSesion(sesionLote);
          await solicitarVista();
          demoraReintento = 500;
          continue;
        } catch (falloRecuperacion) { error = falloRecuperacion; }
      }
      if (!solicitando) mostrarError(error);
      demora = demoraReintento;
      demoraReintento = Math.min(demoraReintento * 2, 8000);
    }
    await new Promise(resolver => setTimeout(resolver, demora));
  }
}

async function procesarLote(buffer, vistaSolicitada, generacionSolicitada) {
  if (vistaSolicitada !== vista || generacionSolicitada !== generacionImagen) return;
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
    if (vistaDato !== vista || generacionSolicitada !== generacionImagen) continue;
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
  // Suavizar la fotografía al ampliarla; en las imágenes numéricas conservar
  // los bordes exactos de los dígitos cuando se alcanza la resolución original.
  contexto.imageSmoothingEnabled = imagen.formato === 'PSB' || nivel !== imagen.maximo;
  contexto.imageSmoothingQuality = 'high';
  contexto.clearRect(0, 0, canvas.clientWidth, canvas.clientHeight);
  if (fondoGeneral && nivel > 0) contexto.drawImage(fondoGeneral,
    region.x * fondoGeneral.width / imagen.ancho,
    region.y * fondoGeneral.height / imagen.alto,
    region.ancho * fondoGeneral.width / imagen.ancho,
    region.alto * fondoGeneral.height / imagen.alto,
    0, 0, canvas.clientWidth, canvas.clientHeight);
  // El último cuadro completo cubre huecos mientras llegan mosaicos de otra zona.
  if (ultimoCuadroCompleto) contexto.drawImage(ultimoCuadroCompleto, 0, 0,
    canvas.clientWidth, canvas.clientHeight);
  // Mantener una imagen de contexto mientras llegan mosaicos mas detallados.
  const disponibles = [...cache].filter(([id]) => {
    const partes = id.split(':');
    return partes[0] === imagen.id && Number(partes[1]) <= nivel;
  }).sort(([idA], [idB]) => {
    const a = idA.split(':'), b = idB.split(':');
    return Number(a[1]) - Number(b[1]) || Number(a[4] === 'F') - Number(b[4] === 'F');
  });
  let dibujados = 0;
  for (const [id, mosaico] of disponibles) {
    const partes = id.split(':');
    // A máximo acercamiento, una miniatura ampliada puede ser solo un color.
    // Si existe un cuadro completo, conservarlo hasta recibir mosaicos del nivel actual.
    if (ultimoCuadroCompleto && imagen.formato === 'PNG' && nivel === imagen.maximo &&
        Number(partes[1]) < nivel) continue;
    const lado = 256 * 2 ** (imagen.maximo - Number(partes[1]));
    const x = Number(partes[2]) * lado, y = Number(partes[3]) * lado;
    if (x + lado < region.x || y + lado < region.y || x > region.x + region.ancho || y > region.y + region.alto) continue;
    const ancho = Math.min(lado, imagen.ancho - x), alto = Math.min(lado, imagen.alto - y);
    contexto.drawImage(mosaico, (x - region.x) * canvas.clientWidth / region.ancho,
      (y - region.y) * canvas.clientHeight / region.alto,
      ancho * canvas.clientWidth / region.ancho, alto * canvas.clientHeight / region.alto);
    dibujados++;
    cache.get(id); // El mosaico dibujado gana frecuencia en GDSF.
  }
  const finales = new Set(cache.keys());
  const ladoActual = 256 * 2 ** (imagen.maximo - nivel);
  const primeraColumna = Math.floor(region.x / ladoActual);
  const ultimaColumna = Math.floor((Math.min(imagen.ancho, Math.ceil(region.x + region.ancho)) - 1) / ladoActual);
  const primeraFila = Math.floor(region.y / ladoActual);
  const ultimaFila = Math.floor((Math.min(imagen.alto, Math.ceil(region.y + region.alto)) - 1) / ladoActual);
  let detalleCompleto = true;
  for (let fila = primeraFila; fila <= ultimaFila; fila++) {
    for (let columna = primeraColumna; columna <= ultimaColumna; columna++) {
      if (!finales.has(`${imagen.id}:${nivel}:${columna}:${fila}:F`)) detalleCompleto = false;
    }
  }
  estadoCarga.hidden = detalleCompleto;
  if (detalleCompleto && (dibujados > 0 || fondoGeneral)) {
    const cuadro = document.createElement('canvas');
    cuadro.width = canvas.width;
    cuadro.height = canvas.height;
    cuadro.getContext('2d').drawImage(canvas, 0, 0);
    ultimoCuadroCompleto = cuadro;
  }
  if (!detalleCompleto && (dibujados > 0 || fondoGeneral))
    estadoCarga.textContent = `Cargando detalle de ${imagen.id}…`;
}

async function actualizarEstado(incluido = null, vistaSolicitada = vista) {
  const datos = new URLSearchParams(incluido ?? await pedirTexto('/protocolo/estado?sesion=' + sesion));
  if (vistaSolicitada !== vista || Number(datos.get('vista')) !== vista) return null;
  return { pendientes: Number(datos.get('pendientes')), enVuelo: Number(datos.get('enVuelo')) };
}

function mostrarError(error) {
  console.error(error);
  if (!estadoCarga.hidden) estadoCarga.textContent = `No se pudo cargar ${imagen?.id ?? 'la imagen'}. Reintentando…`;
}
iniciar().catch(mostrarError);
