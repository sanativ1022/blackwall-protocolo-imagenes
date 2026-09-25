# Validación de la imagen base de 24 GB

Fecha: 25 de septiembre de 2026. Imagen utilizada: `eso1242a.psb` (aproximadamente 24 GB). Esta tabla documenta las pruebas manuales realizadas en Chrome con BlackWall; la imagen original no forma parte del repositorio.

| Prueba | Resultado |
| --- | --- |
| Abrir la imagen y llegar al nivel 9/9 con la región definida | Completada |
| Navegar a 500 Kbps | Completada |
| Navegar a 3G / 400 Kbps | Completada |
| Navegar con latencia alta en Chrome | Completada |
| Cambiar rápidamente de región bajo red lenta | Completada |
| Medir caché y reducción de solicitudes en Chrome | Completada |
| Comprobar recuperación de errores en el navegador | Completada |

## Evidencia observada

- En 3G, el visor llegó a 9/9 y completó las regiones solicitadas con respuestas `lotes` de estado 200.
- En la comprobación de caché, el desplazamiento hacia una región nueva transfirió aproximadamente 385 kB. Al regresar a la región anterior, Network mostró tres solicitudes `vistas`, ninguna `lotes` y 504 B transferidos; los mosaicos se reutilizaron sin volver a descargarlos.
- Para la recuperación de errores se bloqueó temporalmente la URL de `lotes` en DevTools. La región nueva quedó parcialmente vacía y Network registró solicitudes bloqueadas. Al retirar el bloqueo, sin recargar ni mover la imagen, nuevas solicitudes `lotes` respondieron 200 y la región se completó en 9/9. Las filas de error anteriores permanecieron en el historial de DevTools.
- También se verificó visualmente la navegación a 500 Kbps, el perfil 3G/400 Kbps, la latencia alta y los cambios rápidos de región bajo red lenta. Esta validación manual no constituye una medición comparativa formal de rendimiento.

La legibilidad de números **no** es una prueba de la imagen base de 24 GB. Corresponde a la evaluación posterior de las imágenes oficiales de 55 y 93 GB, que contienen los valores mencionados en las indicaciones del proyecto.

La batería automatizada `scripts/Probar.ps1` se ejecutó correctamente antes de crear este registro. El resultado manual queda limitado a la imagen, el navegador y las condiciones descritas; no implica que las imágenes oficiales de 17, 28, 55 o 93 GB ya hayan sido validadas.
