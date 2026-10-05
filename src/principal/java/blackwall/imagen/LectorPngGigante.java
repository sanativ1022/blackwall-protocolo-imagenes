package blackwall.imagen;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Lectura aleatoria del PNG RGB gigante entregado con IDAT de 8192 bytes y DEFLATE almacenado. */
final class LectorPngGigante {
    private static final byte[] FIRMA = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final int BLOQUE_IDAT = 8192;
    private final Path archivo;
    private final int ancho, alto;
    private final long primerIdat, longitudIdat, longitudFila;
    private volatile Indice indice;

    LectorPngGigante(Path archivo, int ancho, int alto) throws IOException {
        this.archivo = archivo;
        this.ancho = ancho;
        this.alto = alto;
        this.longitudFila = 1L + 3L * ancho;
        try (FileChannel canal = FileChannel.open(archivo, StandardOpenOption.READ)) {
            ByteBuffer cabecera = ByteBuffer.allocate(33).order(ByteOrder.BIG_ENDIAN);
            leerExacto(canal, cabecera, 0);
            byte[] bytes = cabecera.array();
            if (!Arrays.equals(Arrays.copyOf(bytes, 8), FIRMA) || !tipo(bytes, 12, "IHDR")
                    || cabecera.getInt(16) != ancho || cabecera.getInt(20) != alto
                    || (bytes[24] & 255) != 8 || (bytes[25] & 255) != 2
                    || bytes[26] != 0 || bytes[27] != 0 || bytes[28] != 0)
                throw new IOException("El PNG gigante requiere RGB de 8 bits sin entrelazado");
            long posicion = 8;
            while (true) {
                ByteBuffer chunk = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
                leerExacto(canal, chunk, posicion);
                byte[] c = chunk.array();
                long largo = Integer.toUnsignedLong(chunk.getInt(0));
                if (tipo(c, 4, "IDAT")) break;
                if (largo > canal.size() - posicion - 12 || tipo(c, 4, "IEND"))
                    throw new IOException("PNG sin datos IDAT validos");
                posicion += largo + 12;
            }
            primerIdat = posicion;
            long ultimoPosible = (canal.size() - posicion) / (BLOQUE_IDAT + 12L);
            long inferior = 0, superior = ultimoPosible + 1;
            while (inferior < superior) {
                long medio = (inferior + superior + 1) >>> 1;
                if (medio <= ultimoPosible && idatCompleto(canal, posicion + medio * (BLOQUE_IDAT + 12L)))
                    inferior = medio;
                else superior = medio - 1;
            }
            long completos = inferior + 1;
            long ultimaPosicion = posicion + completos * (BLOQUE_IDAT + 12L);
            ByteBuffer ultimo = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
            leerExacto(canal, ultimo, ultimaPosicion);
            int longitudFinal = ultimo.getInt(0);
            if (!tipo(ultimo.array(), 4, "IDAT") || longitudFinal <= 0 || longitudFinal > BLOQUE_IDAT)
                throw new IOException("El PNG no usa bloques IDAT uniformes de 8192 bytes");
            longitudIdat = completos * BLOQUE_IDAT + longitudFinal;
            byte[] zlib = new byte[2];
            leerIdat(canal, 0, zlib, 0, 2);
            if ((zlib[0] & 15) != 8 || (((zlib[0] & 255) << 8 | (zlib[1] & 255)) % 31) != 0)
                throw new IOException("Cabecera zlib no valida");
        }
    }

    BufferedImage leer(RegionImagen region, int submuestreo) throws IOException {
        Indice bloques = indice();
        int salidaAncho = (region.ancho() + submuestreo - 1) / submuestreo;
        int salidaAlto = (region.alto() + submuestreo - 1) / submuestreo;
        BufferedImage salida = new BufferedImage(salidaAncho, salidaAlto, BufferedImage.TYPE_INT_RGB);
        int bytesFila = Math.toIntExact(3L * ((long) (salidaAncho - 1) * submuestreo + 1));
        byte[] fila = new byte[bytesFila];
        byte[] filtro = new byte[1];
        try (FileChannel canal = FileChannel.open(archivo, StandardOpenOption.READ)) {
            for (int y = 0; y < salidaAlto; y++) {
                long origenY = region.y() + (long) y * submuestreo;
                long inicio = origenY * longitudFila;
                leerCrudo(canal, bloques, inicio, filtro, 0, 1);
                if (filtro[0] != 0)
                    throw new IOException("La fila " + origenY + " usa filtro PNG " + (filtro[0] & 255)
                            + "; este lector rapido solo admite filas sin filtro");
                leerCrudo(canal, bloques, inicio + 1 + 3L * region.x(), fila, 0, bytesFila);
                for (int x = 0; x < salidaAncho; x++) {
                    int p = 3 * x * submuestreo;
                    salida.setRGB(x, y, (fila[p] & 255) << 16 | (fila[p + 1] & 255) << 8 | fila[p + 2] & 255);
                }
            }
        }
        return salida;
    }

    private Indice indice() throws IOException {
        Indice actual = indice;
        if (actual != null) return actual;
        synchronized (this) {
            if (indice != null) return indice;
            Path cacheIndice = rutaIndice();
            Indice guardado = cargarIndice(cacheIndice);
            if (guardado != null) return indice = guardado;
            try (FileChannel canal = FileChannel.open(archivo, StandardOpenOption.READ)) {
                long[] iniciosCrudos = new long[1024], iniciosDatos = new long[1024];
                int[] longitudes = new int[1024];
                int cantidad = 0;
                long crudo = 0, comprimido = 2;
                while (true) {
                    byte[] cabecera = new byte[5];
                    leerIdat(canal, comprimido, cabecera, 0, 5);
                    int tipo = cabecera[0] & 6;
                    int largo = (cabecera[1] & 255) | (cabecera[2] & 255) << 8;
                    int inverso = (cabecera[3] & 255) | (cabecera[4] & 255) << 8;
                    if (tipo != 0 || largo != (~inverso & 65535) || largo == 0)
                        throw new IOException("El PNG gigante no usa exclusivamente bloques DEFLATE almacenados");
                    if (cantidad == iniciosCrudos.length) {
                        int nuevo = Math.multiplyExact(cantidad, 2);
                        iniciosCrudos = Arrays.copyOf(iniciosCrudos, nuevo);
                        iniciosDatos = Arrays.copyOf(iniciosDatos, nuevo);
                        longitudes = Arrays.copyOf(longitudes, nuevo);
                    }
                    iniciosCrudos[cantidad] = crudo;
                    iniciosDatos[cantidad] = comprimido + 5;
                    longitudes[cantidad++] = largo;
                    crudo += largo;
                    comprimido += 5L + largo;
                    if ((cabecera[0] & 1) != 0) break;
                    if (comprimido + 5 > longitudIdat) throw new IOException("Flujo DEFLATE truncado");
                }
                if (crudo != longitudFila * alto)
                    throw new IOException("Longitud de pixeles PNG incoherente: " + crudo);
                indice = new Indice(Arrays.copyOf(iniciosCrudos, cantidad),
                        Arrays.copyOf(iniciosDatos, cantidad), Arrays.copyOf(longitudes, cantidad));
                try { guardarIndice(cacheIndice, indice); }
                catch (IOException ignorada) { /* La imagen sigue siendo legible sin cache persistente. */ }
                return indice;
            }
        }
    }

    private Path rutaIndice() throws IOException {
        byte[] ruta = archivo.toAbsolutePath().normalize().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] digest;
        try { digest = MessageDigest.getInstance("SHA-256").digest(ruta); }
        catch (NoSuchAlgorithmException e) { throw new IOException("SHA-256 no disponible", e); }
        return Path.of("datos", "indices", java.util.HexFormat.of().formatHex(digest) + ".bwi");
    }

    private Indice cargarIndice(Path ruta) throws IOException {
        if (!Files.isRegularFile(ruta)) return null;
        try (DataInputStream entrada = new DataInputStream(new BufferedInputStream(Files.newInputStream(ruta)))) {
            if (entrada.readInt() != 0x42574958 || entrada.readInt() != 1
                    || entrada.readLong() != Files.size(archivo)
                    || entrada.readLong() != Files.getLastModifiedTime(archivo).toMillis()
                    || entrada.readLong() != longitudFila * alto
                    || entrada.readLong() != longitudIdat) return null;
            int cantidad = entrada.readInt();
            if (cantidad <= 0 || cantidad > (longitudFila * alto + 32767) / 32768) return null;
            long[] crudos = new long[cantidad], datos = new long[cantidad];
            int[] largos = new int[cantidad];
            long siguiente = 0;
            for (int i = 0; i < cantidad; i++) {
                crudos[i] = entrada.readLong();
                datos[i] = entrada.readLong();
                largos[i] = entrada.readInt();
                if (crudos[i] != siguiente || datos[i] < 7 || datos[i] + largos[i] > longitudIdat
                        || largos[i] <= 0 || largos[i] > 65535) return null;
                siguiente += largos[i];
            }
            if (siguiente != longitudFila * alto || entrada.read() != -1) return null;
            return new Indice(crudos, datos, largos);
        } catch (IOException | NegativeArraySizeException fallo) {
            return null;
        }
    }

    private void guardarIndice(Path ruta, Indice bloques) throws IOException {
        Files.createDirectories(ruta.getParent());
        Path temporal = Files.createTempFile(ruta.getParent(), "indice-", ".tmp");
        try {
            try (DataOutputStream salida = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporal)))) {
                salida.writeInt(0x42574958);
                salida.writeInt(1);
                salida.writeLong(Files.size(archivo));
                salida.writeLong(Files.getLastModifiedTime(archivo).toMillis());
                salida.writeLong(longitudFila * alto);
                salida.writeLong(longitudIdat);
                salida.writeInt(bloques.longitudes.length);
                for (int i = 0; i < bloques.longitudes.length; i++) {
                    salida.writeLong(bloques.iniciosCrudos[i]);
                    salida.writeLong(bloques.iniciosDatos[i]);
                    salida.writeInt(bloques.longitudes[i]);
                }
            }
            try { Files.move(temporal, ruta, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporal, ruta, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporal); }
    }

    private void leerCrudo(FileChannel canal, Indice bloques, long posicion, byte[] destino, int desde, int largo) throws IOException {
        while (largo > 0) {
            int i = Arrays.binarySearch(bloques.iniciosCrudos, posicion);
            if (i < 0) i = -i - 2;
            if (i < 0 || posicion >= bloques.iniciosCrudos[i] + bloques.longitudes[i])
                throw new IOException("Posicion de pixel fuera del flujo PNG");
            int tomar = (int) Math.min(largo, bloques.iniciosCrudos[i] + bloques.longitudes[i] - posicion);
            leerIdat(canal, bloques.iniciosDatos[i] + posicion - bloques.iniciosCrudos[i], destino, desde, tomar);
            posicion += tomar;
            desde += tomar;
            largo -= tomar;
        }
    }

    private void leerIdat(FileChannel canal, long posicion, byte[] destino, int desde, int largo) throws IOException {
        if (posicion < 0 || posicion + largo > longitudIdat)
            throw new IOException("Datos IDAT truncados: posicion=" + posicion + ", largo=" + largo
                    + ", total=" + longitudIdat);
        while (largo > 0) {
            int dentro = (int) (posicion % BLOQUE_IDAT);
            int tomar = Math.min(largo, BLOQUE_IDAT - dentro);
            long archivoPosicion = primerIdat + 8 + posicion + 12 * (posicion / BLOQUE_IDAT);
            leerExacto(canal, ByteBuffer.wrap(destino, desde, tomar), archivoPosicion);
            posicion += tomar;
            desde += tomar;
            largo -= tomar;
        }
    }

    private static boolean idatCompleto(FileChannel canal, long posicion) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
        leerExacto(canal, b, posicion);
        return b.getInt(0) == BLOQUE_IDAT && tipo(b.array(), 4, "IDAT");
    }

    private static boolean tipo(byte[] bytes, int posicion, String tipo) {
        for (int i = 0; i < 4; i++) if (bytes[posicion + i] != tipo.charAt(i)) return false;
        return true;
    }

    private static void leerExacto(FileChannel canal, ByteBuffer buffer, long posicion) throws IOException {
        while (buffer.hasRemaining()) {
            int leidos = canal.read(buffer, posicion);
            if (leidos < 0) throw new IOException("Archivo PNG truncado");
            posicion += leidos;
        }
    }

    private record Indice(long[] iniciosCrudos, long[] iniciosDatos, int[] longitudes) { }
}
