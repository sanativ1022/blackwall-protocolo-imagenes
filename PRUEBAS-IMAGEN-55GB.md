# Pruebas con la imagen oficial de 55 GB

Fecha: 27 de septiembre de 2026. Archivo: `055-843-000-80450114.png`, 55,843,161,368 bytes, 136325 × 136325 píxeles. Las pruebas no modificaron la imagen.

| Aspecto | Evidencia | Resultado |
| --- | --- | --- |
| Lectura regional del PNG oficial | `PruebasPngGigante` leyó esquina, centro, borde y vista general; comprobó metadatos y dimensiones. | Aprobado |
| Nitidez de los números | Observación manual en Chrome al nivel 10/10; dígitos distinguibles, con muestreo de píxel más cercano en el visor. | Aprobado visualmente |
| Recuperación RACK-TLP/SACK | `ProbarCambiosYPerdidas.mjs` contra el servidor de la imagen oficial: hueco simulado, secuencia 1 recuperada, una retransmisión, vista completada. En Chrome, bloqueo temporal de `/protocolo/lotes`, desbloqueo y nuevos lotes `200` sin recargar. | Aprobado para los fallos simulados y observados |
| Cambio rápido de zona con 3G en Chrome | Se hicieron seis arrastres consecutivos en 10/10 sin recargar. La captura final muestra 3G activo, la última zona visible y lotes `200` de aproximadamente 18–33 kB; Network registró 411 kB transferidos. `ProbarCambiosYPerdidas.mjs` verificó además que la vista antigua se ignora y la nueva se completa. | Aprobado |
| Red lenta | `ProbarRedLenta.mjs` a 400 kbps y 2000 ms de latencia: previa 5102 ms, detalle final 7720 ms, dos mosaicos, 49,921 bytes; revisita con cero mosaicos. La lentitud se emuló en el cliente de prueba, no en Chrome. | Aprobado en la emulación |
| Navegación a 500 kbps en Chrome | Con Network limpio y el perfil `500 Kps` activo, se navegó a otra zona de la imagen oficial y se regresó a 10/10. La captura final muestra números legibles, respuestas `lotes` 200 y 893 kB transferidos. | Aprobado |
| Navegación con latencia alta en Chrome | Con Network limpio y el perfil `Latencia alta` activo, se navegó a otra zona y se regresó a 10/10. La captura final muestra números legibles, solicitudes `lotes` 200 de aproximadamente 3.03–3.06 s y 379 kB transferidos. | Aprobado |
| Reutilización de caché entre niveles | En Chrome, al volver de 8/10 a 10/10 tras limpiar Network: cuatro solicitudes `vistas`, 672 B transferidos y ninguna solicitud `lotes`. | Aprobado en ese recorrido |
| Expulsión GDSF | `ProbarCacheCliente.mjs`: en el límite real de 128 entradas, la entrada 129 expulsa una de menor valor y conserva la frecuente; en el límite real de 64 MiB conserva el bloque costoso y expulsa el barato. Verifica envejecimiento y contabilidad. | Aprobado determinísticamente en la política cliente |
| Reacción del servidor al estado de caché | `PruebasIntegracionServidor`: un bloque declarado en caché evita envío; uno ausente se programa y retransmite. | Aprobado con imagen sintética de integración |

La batería `scripts/Probar.ps1` terminó con código 0. También terminaron con código 0 `PruebasPngGigante`, `ProbarCambiosYPerdidas.mjs` y `ProbarRedLenta.mjs` contra el servidor activo de 55 GB.

## Alcance

Estas pruebas comprueban las condiciones descritas arriba; no son una garantía absoluta para cualquier patrón de navegación, avería o equipo. La política de expulsión GDSF se forzó y comprobó de forma determinista; no se llenó manualmente la caché del navegador con 128 zonas de la imagen oficial. La emulación de red lenta es un cliente de prueba y no una medición del ancho de banda de Chrome.
