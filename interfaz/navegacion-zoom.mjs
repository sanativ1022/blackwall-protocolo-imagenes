const FACTOR_ACERCAR = 0.55;

export function cambiarZoom({ nivel, region, direccion, pivoteX, pivoteY, imagen, historial }) {
  const siguienteNivel = Math.max(0, Math.min(imagen.maximo, nivel + direccion));
  if (siguienteNivel === nivel) return { nivel, region };

  const anterior = historial.at(-1);
  if (anterior && anterior.direccion === -direccion && anterior.nivelDespues === nivel &&
      anterior.pivoteX === pivoteX && anterior.pivoteY === pivoteY &&
      Object.keys(region).every(clave => region[clave] === anterior.regionDespues[clave])) {
    historial.pop();
    return { nivel: anterior.nivelAntes, region: { ...anterior.regionAntes } };
  }

  const factor = direccion > 0 ? FACTOR_ACERCAR : 1 / FACTOR_ACERCAR;
  const centroX = region.x + region.ancho * pivoteX;
  const centroY = region.y + region.alto * pivoteY;
  const ancho = Math.max(64, Math.min(imagen.ancho, region.ancho * factor));
  const alto = Math.max(64, Math.min(imagen.alto, region.alto * factor));
  const nuevaRegion = {
    x: Math.max(0, Math.min(imagen.ancho - ancho, centroX - ancho * pivoteX)),
    y: Math.max(0, Math.min(imagen.alto - alto, centroY - alto * pivoteY)),
    ancho, alto
  };
  historial.push({ direccion, pivoteX, pivoteY, nivelAntes: nivel,
    regionAntes: { ...region }, nivelDespues: siguienteNivel, regionDespues: { ...nuevaRegion } });
  if (historial.length > 100) historial.shift();
  return { nivel: siguienteNivel, region: nuevaRegion };
}
