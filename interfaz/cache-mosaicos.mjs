// GDSF adaptado: H = edad + frecuencia * coste de recuperar / bytes en RAM.
export class CacheMosaicos {
  constructor(maximoEntradas = 128, maximoBytes = 64 * 1024 * 1024) {
    this.maximoEntradas = maximoEntradas;
    this.maximoBytes = maximoBytes;
    this.datos = new Map();
    this.bytes = 0;
    this.edad = 0;
  }

  get size() { return this.datos.size; }
  keys() { return this.datos.keys(); }
  *[Symbol.iterator]() {
    for (const [id, entrada] of this.datos) yield [id, entrada.mosaico];
  }

  get(id) {
    const entrada = this.datos.get(id);
    if (!entrada) return undefined;
    entrada.frecuencia++;
    entrada.valor = this.edad + entrada.frecuencia * entrada.coste / entrada.bytes;
    return entrada.mosaico;
  }

  delete(id) {
    const entrada = this.datos.get(id);
    if (!entrada) return false;
    this.bytes -= entrada.bytes;
    return this.datos.delete(id);
  }

  set(id, mosaico, costeRecuperacion = 1) {
    const bytes = mosaico.width * mosaico.height * 4;
    if (bytes <= 0 || bytes > this.maximoBytes) return this;
    const frecuencia = this.datos.get(id)?.frecuencia ?? 1;
    this.delete(id);
    const coste = Math.max(1, costeRecuperacion);
    this.datos.set(id, { mosaico, bytes, coste, frecuencia,
      valor: this.edad + frecuencia * coste / bytes });
    this.bytes += bytes;
    while (this.datos.size > this.maximoEntradas || this.bytes > this.maximoBytes) {
      let victima, valorMinimo = Infinity;
      for (const [clave, entrada] of this.datos) {
        if (entrada.valor < valorMinimo) { victima = clave; valorMinimo = entrada.valor; }
      }
      this.edad = valorMinimo;
      this.delete(victima);
    }
    return this;
  }
}
