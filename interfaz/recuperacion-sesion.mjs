export function esSesionDesconocida(error) {
  return error instanceof Error && error.message.trim() === 'error=Sesion desconocida';
}

export function crearRenovadorSesion(crear, obtener, actualizar) {
  let pendiente = null;
  return async sesionFallida => {
    if (obtener() !== sesionFallida) return;
    if (!pendiente) {
      pendiente = Promise.resolve().then(crear).then(nueva => {
        if (obtener() === sesionFallida) actualizar(nueva);
      }).finally(() => { pendiente = null; });
    }
    await pendiente;
  };
}
