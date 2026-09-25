import assert from 'node:assert/strict';

const base = process.argv[2] ?? 'http://127.0.0.1:8083';
async function post(path, data) {
  const response = await fetch(base + path, { method: 'POST', body: new URLSearchParams(data) });
  if (!response.ok) throw Error(`${path}: ${response.status} ${await response.text()}`);
  return response;
}
async function state(session) {
  const response = await fetch(`${base}/protocolo/estado?sesion=${session}`);
  assert.equal(response.status, 200);
  return new URLSearchParams(await response.text());
}
async function batch(data) {
  const response = await post('/protocolo/lotes', data);
  const bytes = Buffer.from(await response.arrayBuffer());
  let offset = 4;
  const sequences = [];
  const count = bytes.readInt32BE(0);
  for (let i = 0; i < count; i++) {
    const size = bytes.readInt32BE(offset); offset += 4;
    assert.equal(bytes.readUInt32BE(offset), 0x42574931);
    sequences.push(Number(bytes.readBigInt64BE(offset + 6)));
    offset += size;
  }
  assert.equal(offset, bytes.length);
  return sequences;
}
const [image, , , level] = (await (await fetch(base + '/protocolo/imagenes')).text()).trim().split('|');
const newSession = async () => new URLSearchParams(await (await post('/protocolo/sesiones', {})).text()).get('sesion');
const view = (session, id, x) => post('/protocolo/vistas', {
  sesion: session, imagen: image, vista: id, x, y: 40448, ancho: 256, alto: 256, nivel: level, ventana: 16
});

const movement = await newSession();
await view(movement, 1, 53760);
assert.deepEqual(await batch({ sesion: movement, vista: 1, ack: 0, ventana: 16 }), [1]);
await view(movement, 2, 54784);
assert.deepEqual(await batch({ sesion: movement, vista: 1, ack: 0, ventana: 16 }), []);
assert.deepEqual(await batch({ sesion: movement, vista: 2, ack: 0, ventana: 16 }), [1]);
assert.deepEqual(await batch({ sesion: movement, vista: 2, ack: 1, ventana: 16 }), [2]);
assert.deepEqual(await batch({ sesion: movement, vista: 2, ack: 2, ventana: 16 }), []);
const movedState = await state(movement);
assert.equal(movedState.get('enVuelo'), '0');
assert.equal(movedState.get('pendientes'), '0');

const loss = await newSession();
await view(loss, 1, 53760);
assert.deepEqual(await batch({ sesion: loss, vista: 1, ack: 0, ventana: 16 }), [1]);
assert.deepEqual(await batch({ sesion: loss, vista: 1, ack: 0, ventana: 16 }), [2]);
const gap = { sesion: loss, vista: 1, ack: 0, sack: '2-2', ventana: 16 };
let recovered = [];
for (let attempt = 0; attempt < 4 && recovered.length === 0; attempt++) {
  recovered = await batch(gap);
  if (recovered.length === 0) await new Promise(resolve => setTimeout(resolve, 5));
}
assert.deepEqual(recovered, [1], 'RACK recupera el hueco confirmado por SACK');
assert.deepEqual(await batch({ sesion: loss, vista: 1, ack: 2, ventana: 16 }), []);
const recoveredState = await state(loss);
assert.equal(recoveredState.get('retransmisiones'), '1');
assert.equal(recoveredState.get('enVuelo'), '0');
console.log(JSON.stringify({ image, level: Number(level), rapidChange: {
  oldViewIgnored: true, newViewCompleted: true, discarded: Number(movedState.get('descartados'))
}, simulatedLoss: { recoveredSequence: 1, retransmissions: 1, completed: true } }, null, 2));
