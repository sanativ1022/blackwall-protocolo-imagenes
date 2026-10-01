import assert from 'node:assert/strict';
import { cambiarZoom } from '../interfaz/navegacion-zoom.mjs';

const imagen = { ancho: 176393, alto: 176393, maximo: 10 };
const inicio = { x: 145000, y: 130000, ancho: 480, alto: 360 };
const historial = [];
let estado = { nivel: 10, region: { ...inicio } };
const cambiar = (direccion, pivoteX = 0.5, pivoteY = 0.5) => {
  estado = cambiarZoom({ ...estado, direccion, pivoteX, pivoteY, imagen, historial });
};

for (let i = 0; i < 10; i++) cambiar(-1);
assert.equal(estado.nivel, 0);
for (let i = 0; i < 10; i++) cambiar(1);
assert.deepEqual(estado, { nivel: 10, region: inicio });
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
assert.deepEqual(estado, { nivel: 4, region: zonaNueva });

const enLimite = { ...estado };
cambiar(-1);
assert.equal(estado.nivel, 3);
cambiar(1);
assert.deepEqual(estado, enLimite);

console.log('[OK] Zoom 10→0→10 conserva la región; un arrastre cambia de zona y el recorrido inverso vuelve a ella.');
