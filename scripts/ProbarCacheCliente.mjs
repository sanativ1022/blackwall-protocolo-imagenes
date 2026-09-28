import { CacheMosaicos } from '../interfaz/cache-mosaicos.mjs';
import assert from 'node:assert/strict';

const mosaico = () => ({ width: 256, height: 256 });
const cache = new CacheMosaicos(3, 3 * 256 * 256 * 4);
cache.set('a', mosaico()).set('b', mosaico()).set('c', mosaico());
assert.equal(cache.bytes, 3 * 256 * 256 * 4);
assert.ok(cache.get('a'));
cache.set('d', mosaico());
assert.deepEqual([...cache.keys()], ['a', 'c', 'd']);
assert.equal(cache.get('b'), undefined);
cache.set('a', mosaico());
assert.equal(cache.bytes, 3 * 256 * 256 * 4);
cache.delete('c');
assert.equal(cache.bytes, 2 * 256 * 256 * 4);
const porBytes = new CacheMosaicos(10, 256 * 256 * 4);
porBytes.set('a', mosaico()).set('b', mosaico());
assert.deepEqual([...porBytes.keys()], ['b']);
const coste = new CacheMosaicos(2, 4 * 256 * 256 * 4);
coste.set('barato', mosaico(), 100).set('caro', mosaico(), 1000).set('nuevo', mosaico(), 200);
assert.deepEqual([...coste.keys()], ['caro', 'nuevo']);
const frecuencia = new CacheMosaicos(2, 4 * 256 * 256 * 4);
frecuencia.set('frecuente', mosaico(), 100);
frecuencia.get('frecuente');
frecuencia.get('frecuente');
frecuencia.set('ocasional', mosaico(), 100).set('nuevo', mosaico(), 100);
assert.deepEqual([...frecuencia.keys()], ['frecuente', 'nuevo']);

// Forzar el límite real de producción (128 entradas): una entrada cara y
// frecuentemente usada debe sobrevivir a la 129.ª, aunque sea la más antigua.
const limiteReal = new CacheMosaicos();
for (let i = 0; i < 128; i++) limiteReal.set(`bloque-${i}`, mosaico(), 100);
assert.equal(limiteReal.size, 128);
assert.equal(limiteReal.bytes, 128 * 256 * 256 * 4);
limiteReal.get('bloque-0');
limiteReal.get('bloque-0');
limiteReal.set('bloque-128', mosaico(), 100);
assert.equal(limiteReal.size, 128);
assert.equal(limiteReal.bytes, 128 * 256 * 256 * 4);
assert.ok(limiteReal.get('bloque-0'), 'sobrevive el bloque frecuente');
assert.equal(limiteReal.get('bloque-1'), undefined, 'sale el primer bloque de menor valor');
assert.ok(limiteReal.get('bloque-128'), 'entra el bloque nuevo');
console.log('[OK] GDSF: expulsión real en la entrada 129/128; sobrevive el bloque frecuente.');

// Forzar el límite real de 64 MiB con mosaicos grandes, sin alcanzar 128 entradas.
const limiteBytesReal = new CacheMosaicos();
const grande = () => ({ width: 2048, height: 2048 }); // 16 MiB decodificados
limiteBytesReal.set('valioso', grande(), 1000);
for (let i = 0; i < 3; i++) limiteBytesReal.set(`barato-${i}`, grande(), 100);
assert.equal(limiteBytesReal.bytes, 64 * 1024 * 1024);
limiteBytesReal.set('quinto', grande(), 100);
assert.equal(limiteBytesReal.size, 4);
assert.equal(limiteBytesReal.bytes, 64 * 1024 * 1024);
assert.ok(limiteBytesReal.get('valioso'), 'sobrevive el bloque caro');
assert.equal(limiteBytesReal.get('barato-0'), undefined, 'sale el bloque barato');
assert.ok(limiteBytesReal.get('quinto'));
console.log('[OK] GDSF: expulsión real por 64 MiB; sobrevive el bloque costoso.');

// El valor de envejecimiento debe aumentar tras expulsar y la contabilidad
// debe seguir correcta al sustituir o eliminar una entrada.
assert.ok(limiteReal.edad > 0);
const bytesAntes = limiteReal.bytes;
limiteReal.set('bloque-0', mosaico(), 100);
assert.equal(limiteReal.bytes, bytesAntes);
assert.ok(limiteReal.delete('bloque-0'));
assert.equal(limiteReal.bytes, bytesAntes - 256 * 256 * 4);
assert.equal(limiteReal.delete('bloque-0'), false);
console.log('[OK] GDSF: envejecimiento, reemplazo y contabilidad de memoria.');
