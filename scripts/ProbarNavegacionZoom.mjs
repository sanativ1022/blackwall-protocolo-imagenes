import assert from 'node:assert/strict';
import { cambiarZoom } from '../interfaz/navegacion-zoom.mjs';

const imagen = { ancho: 176393, alto: 176393, maximo: 10, formato: 'PNG' };
const inicio = { x: 145000, y: 130000, ancho: 480, alto: 360 };
const historial = [];
let estado = { nivel: 10, zoomExtra: 0, region: { ...inicio } };
const cambiar = (direccion, pivoteX = 0.5, pivoteY = 0.5) => {
  estado = cambiarZoom({ ...estado, direccion, pivoteX, pivoteY, imagen, historial });
};

for (let i = 0; i < 10; i++) cambiar(-1);
assert.equal(estado.nivel, 0);
for (let i = 0; i < 10; i++) cambiar(1);
assert.deepEqual(estado, { nivel: 10, zoomExtra: 0, region: inicio });
assert.equal(historial.length, 0);

for (let i = 0; i < 6; i++) cambiar(-1);
assert.equal(estado.nivel, 4);
historial.length = 0; // Un arrastre a otra zona invalida el regreso anterior.
estado.region.x = 50000;
estado.region.y = 60000;
const zonaNueva = { ...estado.region };
for (let i = 0; i < 6; i++) cambiar(1);
assert.equal(estado.nivel, 10);
for (let i = 0; i < 6; i++) cambiar(-1);
assert.deepEqual(estado, { nivel: 4, zoomExtra: 0, region: zonaNueva });

const enLimite = { ...estado };
cambiar(-1);
assert.equal(estado.nivel, 3);
cambiar(1);
assert.deepEqual(estado, enLimite);

for (let i = 0; i < 9; i++) cambiar(1);
assert.equal(estado.nivel, 10);
assert.equal(estado.zoomExtra, 3);
const detalle = { ...estado, region: { ...estado.region } };
cambiar(1);
assert.deepEqual(estado, detalle);
assert.ok(estado.region.ancho < inicio.ancho);
assert.ok(estado.region.alto > 0);
for (let i = 0; i < 3; i++) cambiar(-1);
assert.equal(estado.nivel, 10);
assert.equal(estado.zoomExtra, 0);
cambiar(-1);
assert.equal(estado.nivel, 9);

const foto = { ancho: 108199, alto: 81503, maximo: 9, formato: 'PSB' };
const limiteFoto = cambiarZoom({ nivel: 9, zoomExtra: 0, region: inicio, direccion: 1,
  pivoteX: 0.5, pivoteY: 0.5, imagen: foto, historial: [] });
assert.deepEqual(limiteFoto, { nivel: 9, zoomExtra: 0, region: inicio });

console.log('[OK] Zoom y acercamiento adicional conservan la región al regresar; el arrastre cambia de zona.');
