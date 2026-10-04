import assert from 'node:assert/strict';
import { crearRenovadorSesion, esSesionDesconocida } from '../interfaz/recuperacion-sesion.mjs';

assert.equal(esSesionDesconocida(new Error('error=Sesion desconocida')), true);
assert.equal(esSesionDesconocida(new Error('error=Imagen desconocida')), false);

let actual = 'vieja';
let creaciones = 0;
let reinicios = 0;
const renovar = crearRenovadorSesion(
  async () => { creaciones++; await new Promise(resolver => setTimeout(resolver, 10)); return 'nueva'; },
  () => actual,
  nueva => { actual = nueva; reinicios++; }
);
await Promise.all([renovar('vieja'), renovar('vieja'), renovar('vieja')]);
assert.equal(actual, 'nueva');
assert.equal(creaciones, 1, 'las solicitudes simultaneas comparten una sola renovacion');
assert.equal(reinicios, 1);
await renovar('vieja');
assert.equal(creaciones, 1, 'una respuesta vieja no reemplaza la sesion vigente');

let intentos = 0;
const fallida = crearRenovadorSesion(
  async () => { intentos++; throw new Error('sin servidor'); },
  () => actual,
  nueva => { actual = nueva; }
);
await assert.rejects(fallida('nueva'), /sin servidor/);
await assert.rejects(fallida('nueva'), /sin servidor/);
assert.equal(intentos, 2, 'un fallo permite reintentar mas tarde');
console.log('[OK] Deteccion y renovacion unica de sesiones expiradas.');
