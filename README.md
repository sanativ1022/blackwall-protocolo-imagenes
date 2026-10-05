# BlackWall: visor de imágenes grandes

BlackWall permite explorar imágenes de gran tamaño desde un navegador sin cargar el archivo completo en memoria. El servidor lee la región solicitada, la divide en mosaicos y envía primero una vista previa y después el detalle. El navegador conserva los mosaicos útiles, permite desplazarse y cambiar de nivel de zoom, y solicita de nuevo los datos que falten.

El proyecto incluye un servidor en Java, una interfaz web en HTML y JavaScript, pruebas automatizadas y registros de las pruebas manuales con las imágenes de 24, 55 y 93 GB. Los archivos de imagen no están incluidos en el repositorio.

## Requisitos

- Windows con PowerShell y GNU Make disponible como `make` en la terminal.
- JDK 21 o posterior, con `java` y `javac` disponibles en `PATH`. La compilación utiliza `javac --release 21`.
- Node.js disponible como `node` para ejecutar las pruebas de JavaScript con `make test`.
- Un navegador moderno para usar el visor. Las pruebas manuales del proyecto se realizaron en Chrome.
- Al menos una imagen local compatible. El lector regional admite PSB RGB de 8 bits, tres canales y sin compresión. Para PNG gigantes, el lector rápido está preparado para los archivos oficiales con RGB de 8 bits, sin entrelazado, filas sin filtro y bloques IDAT uniformes de 8192 bytes. Otros formatos legibles por Java ImageIO pueden funcionar cuando la imagen cabe en los límites de ese lector; no se garantiza la lectura regional de cualquier archivo.

## Inicio rápido

Ejecuta estos pasos desde la raíz del proyecto en la terminal de Visual Studio Code. Primero crea la configuración local:

```powershell
Copy-Item .\imagenes.example.txt .\imagenes.local.txt
```

Abre `imagenes.local.txt` y sustituye las rutas de ejemplo por las rutas absolutas de tus archivos. Usa una línea por imagen:

```text
imagen24=C:\ruta\a\imagen.psb
imagen55=C:\ruta\a\imagen55.png
imagen93=C:\ruta\a\imagen93.png
```

Puedes dejar solamente las imágenes que tengas. El identificador de la izquierda es el nombre que aparecerá en el selector del visor. Las líneas que comienzan con `#` se ignoran. `imagenes.local.txt` está excluido de Git; no subas los archivos grandes ni sus rutas personales.

Después, comprueba la configuración, compila e inicia el servidor:

```powershell
make list-images
make build
make server
```

Cuando aparezca `Servidor disponible`, abre [http://127.0.0.1:8080](http://127.0.0.1:8080) en el navegador. El servidor permanece ejecutándose en esa terminal; detenlo con `Ctrl+C`. Escucha únicamente en `127.0.0.1`, por lo que no expone el visor a otros equipos de la red.

`make server` también compila antes de iniciar. Si ya verificaste la compilación con `make build`, puedes ejecutar directamente `make server`. Para otro puerto, usa `make server PORT=9090` y abre la dirección correspondiente.

## Comandos Make

| Comando | Función |
| --- | --- |
| `make help` | Muestra los comandos disponibles. |
| `make build` | Compila el servidor Java en `construccion/clases`. |
| `make server` | Compila e inicia el servidor con las imágenes de `imagenes.local.txt`. |
| `make run` | Alias de `make server`. |
| `make list-images` | Muestra las imágenes configuradas e indica si existe cada archivo. |
| `make add-image ID=otra FILE="C:/ruta/otra.png"` | Agrega una imagen existente a `imagenes.local.txt`. |
| `make test` | Compila y ejecuta la batería automatizada Java y JavaScript. |
| `make clean` | Borra `construccion`, sin borrar imágenes ni documentos. |

Para registrar otra imagen, puedes editar `imagenes.local.txt` o usar `make add-image`. El identificador debe ser único y contener letras, números, guiones o guiones bajos. Reinicia el servidor después de agregarla: el catálogo se carga al arrancar.

## Cómo funciona

1. Al abrir el visor, el navegador crea una sesión y consulta el catálogo de imágenes.
2. Cada cambio de zona o de zoom envía la región visible, el nivel solicitado y el estado de los mosaicos que el cliente ya tiene en caché.
3. El servidor planifica mosaicos para esa vista y prepara versiones de vista previa y de detalle final. Lee únicamente las regiones necesarias del archivo.
4. El navegador recibe lotes binarios, confirma las tramas recibidas y dibuja los mosaicos. Si cambia de vista, los datos de la vista anterior dejan de ser relevantes.
5. La ventana de transmisión limita los datos en vuelo; las confirmaciones y los huecos detectados permiten ajustar el envío y retransmitir lo que falte.

Las cuatro políticas investigadas se **adaptaron** al protocolo de mosaicos de BlackWall; no se presentan como implementaciones completas de los protocolos originales:

| Adaptación | Uso en BlackWall | Archivo principal |
| --- | --- | --- |
| Flare | Ordena lo visible antes que lo previsto y, cuando hay estabilidad y capacidad, anticipa hasta cuatro mosaicos en la dirección de movimiento. | `src/principal/java/blackwall/imagen/PlanificadorRegiones.java` |
| PCC Vivace | Ajusta la ventana de mosaicos en vuelo con confirmaciones, RTT y pérdidas observadas. | `src/principal/java/blackwall/congestion/ControlCongestionVivace.java` |
| GreedyDual-Size-Frequency | Decide qué mosaicos decodificados conservar en la caché del navegador según uso, coste de recuperación y memoria ocupada. | `interfaz/cache-mosaicos.mjs` |
| RACK-TLP | Usa confirmaciones posteriores, tiempo de reordenamiento y una sonda de cola para decidir cuándo reenviar un mosaico no confirmado. | `src/principal/java/blackwall/sesion/VentanaTransmisionSelectiva.java` |

La transmisión usa HTTP sobre TCP. Las confirmaciones, los rangos SACK y la retransmisión mencionados aquí pertenecen al protocolo de aplicación; no reemplazan los mecanismos del transporte TCP.

En los PNG oficiales gigantes, la primera lectura crea un índice de bloques para acceder a regiones distantes sin decodificar el archivo entero. Ese índice se guarda en `datos/indices`, se comprueba contra el tamaño y la fecha de modificación del PNG y se reutiliza en los siguientes arranques. `make clean` no lo borra. Si el archivo cambia, el índice se reconstruye. El visor puede mostrar una vista previa JPEG mientras llegan los datos, pero mantiene el aviso de carga hasta disponer de los mosaicos finales de la zona visible; esos mosaicos se entregan como PNG sin pérdida.

## Estructura del proyecto

| Ruta | Contenido |
| --- | --- |
| `src/principal/java/blackwall/imagen` | Catálogo, lectores regionales, solicitudes de vista, planificación y codificación de mosaicos. |
| `src/principal/java/blackwall/protocolo` | Formato de tramas, prioridades y confirmaciones. |
| `src/principal/java/blackwall/sesion` | Sesiones, ventana selectiva, estado y trabajos de mosaicos. |
| `src/principal/java/blackwall/congestion` | Control de congestión adaptado y estimación del tiempo de retransmisión. |
| `src/principal/java/blackwall/servidor` | Servicio, servidor HTTP y punto de entrada. |
| `src/pruebas/java/blackwall/pruebas` | Pruebas del núcleo, integración, políticas, lectura regional y sesiones. |
| `interfaz` | Visor web, navegación, caché y recuperación de sesión. |
| `scripts` | Compilación, inicio, pruebas, limpieza y administración de imágenes. |
| `PRUEBAS-IMAGEN-24GB.md`, `PRUEBAS-IMAGEN-55GB.md`, `PRUEBAS-IMAGEN-93GB.md` | Resultados y alcance de las validaciones con archivos grandes. |

## Interfaz HTTP

La interfaz web consume estos puntos del servidor local. Se incluyen para ubicar el flujo, no como sustituto del código fuente:

| Ruta | Función |
| --- | --- |
| `POST /protocolo/sesiones` | Crea una sesión de transferencia. |
| `GET /protocolo/imagenes` | Devuelve los metadatos de las imágenes registradas. |
| `POST /protocolo/vistas` | Solicita una nueva región y comunica la caché del cliente. |
| `POST /protocolo/lotes` | Envía confirmaciones y recibe tramas binarias pendientes. |
| `GET /protocolo/estado?sesion=...` | Consulta contadores y estado de una sesión. |
| `GET /salud` | Comprueba que el servidor responde. |

## Pruebas y evidencias

`make test` ejecuta la batería del núcleo Java, la lectura regional, la integración del servidor, la limpieza de sesiones, las políticas, la adaptación Flare y las pruebas JavaScript de caché, zoom y recuperación de sesión. Estas pruebas usan sus propios datos y no requieren las imágenes oficiales de gran tamaño. La batería de integración puede iniciar servidores de prueba temporales; no inicia el servidor de uso interactivo con `imagenes.local.txt`.

Las pruebas con archivos grandes son adicionales y están registradas por separado:

- [Imagen base de 24 GB](PRUEBAS-IMAGEN-24GB.md): navegación bajo red lenta, caché y recuperación observadas en Chrome.
- [Imagen oficial de 55 GB](PRUEBAS-IMAGEN-55GB.md): lectura regional, nitidez, cambio de zona, caché y recuperación.
- [Imagen oficial de 93 GB](PRUEBAS-IMAGEN-93GB.md): lectura regional, navegación, zoom, red limitada y recuperación.

Los resultados describen los recorridos y condiciones efectivamente probados. `make test` no sustituye una prueba manual del visor con las imágenes que se presentarán.

## Si algo falla

- **`make` no se reconoce:** instala GNU Make y verifica que esté disponible en `PATH` desde la terminal de Visual Studio Code.
- **`javac` o `java` no se reconoce:** instala un JDK 21 o posterior y revisa `PATH`.
- **`node` no se reconoce al ejecutar `make test`:** instala Node.js y abre una terminal nueva.
- **Falta `imagenes.local.txt` o aparece `NO EXISTE`:** copia el archivo de ejemplo y corrige las rutas absolutas. Una ruta no debe apuntar a una carpeta ni a un archivo movido.
- **El formato de imagen se rechaza:** revisa las restricciones de PSB y PNG gigante indicadas en *Requisitos*. Una extensión válida no garantiza que la estructura interna del archivo sea compatible.
- **El puerto está ocupado:** detén la instancia anterior o inicia con `make server PORT=9090`.
- **El visor queda incompleto:** comprueba que el servidor siga activo y revisa en las herramientas de desarrollo del navegador las solicitudes `vistas` y `lotes`. Al volver la conexión, la interfaz puede solicitar los mosaicos pendientes sin reiniciar la sesión.
- **Un PNG grande tarda en aparecer la primera vez:** espera a que termine la preparación de su índice. La siguiente apertura debe reutilizarlo desde `datos/indices`; no hace falta copiar el PNG ni volver a configurarlo. Si el mensaje cambia a un error, revisa la respuesta de `lotes` en Network.

Este proyecto trabaja con archivos locales y grandes volúmenes de datos. Mantén espacio libre suficiente para las imágenes y evita copiarlas dentro de `datos` o del repositorio: `datos` está reservado como carpeta local y su contenido se ignora en Git.
