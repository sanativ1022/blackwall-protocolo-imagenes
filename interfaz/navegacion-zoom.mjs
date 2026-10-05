const FACTOR_ACERCAR = 0.55;
const MAXIMO_DETALLE_EXTRA = 3;

export function cambiarZoom({ nivel, zoomExtra = 0, region, direccion, pivoteX, pivoteY, imagen, historial }) {
  const siguienteExtra = direccion > 0 && nivel === imagen.maximo && imagen.formato === 'PNG'
    ? Math.min(MAXIMO_DETALLE_EXTRA, zoomExtra + 1)
    : direccion < 0 && zoomExtra > 0 ? zoomExtra - 1 : zoomExtra;
  const siguienteNivel = siguienteExtra !== zoomExtra ? nivel
    : Math.max(0, Math.min(imagen.maximo, nivel + direccion));
  if (siguienteNivel === nivel && siguienteExtra === zoomExtra) return { nivel, zoomExtra, region };

  const anterior = historial.at(-1);
  if (anterior && anterior.direccion === -direccion && anterior.nivelDespues === nivel &&
      anterior.extraDespues === zoomExtra &&
      anterior.pivoteX === pivoteX && anterior.pivoteY === pivoteY &&
      Object.keys(region).every(clave => region[clave] === anterior.regionDespues[clave])) {
    historial.pop();
    return { nivel: anterior.nivelAntes, zoomExtra: anterior.extraAntes, region: { ...anterior.regionAntes } };
  }

  const factor = direccion > 0 ? FACTOR_ACERCAR : 1 / FACTOR_ACERCAR;
  const centroX = region.x + region.ancho * pivoteX;
  const centroY = region.y + region.alto * pivoteY;
  const ancho = Math.max(16, Math.min(imagen.ancho, region.ancho * factor));
  const alto = Math.max(16, Math.min(imagen.alto, region.alto * factor));
  const nuevaRegion = {
    x: Math.max(0, Math.min(imagen.ancho - ancho, centroX - ancho * pivoteX)),
    y: Math.max(0, Math.min(imagen.alto - alto, centroY - alto * pivoteY)),
    ancho, alto
  };
  historial.push({ direccion, pivoteX, pivoteY, nivelAntes: nivel, extraAntes: zoomExtra,
    regionAntes: { ...region }, nivelDespues: siguienteNivel, extraDespues: siguienteExtra,
    regionDespues: { ...nuevaRegion } });
  if (historial.length > 100) historial.shift();
  return { nivel: siguienteNivel, zoomExtra: siguienteExtra, region: nuevaRegion };
}
