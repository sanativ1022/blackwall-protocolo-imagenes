package blackwall.imagen;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

/** Lee regiones de PSB RGB planar sin descomprimir ni materializar la imagen completa. */
final class LectorPsbRegional {
    record Cabecera(int ancho, int alto, long inicioPixeles) {}

    static Cabecera inspeccionar(Path archivo) throws IOException {
        try (RandomAccessFile entrada = new RandomAccessFile(archivo.toFile(), "r")) {
            if (entrada.readInt() != 0x38425053 || entrada.readUnsignedShort() != 2)
                throw new IOException("No es un archivo PSB valido");
            entrada.seek(12);
            int canales = entrada.readUnsignedShort();
            int alto = entrada.readInt(), ancho = entrada.readInt();
            int profundidad = entrada.readUnsignedShort(), modoColor = entrada.readUnsignedShort();
            if (canales != 3 || profundidad != 8 || modoColor != 3 || alto <= 0 || ancho <= 0)
                throw new IOException("PSB no soportado: se requiere RGB de 8 bits y tres canales");
            saltar(entrada, Integer.toUnsignedLong(entrada.readInt())); // datos del modo de color
            saltar(entrada, Integer.toUnsignedLong(entrada.readInt())); // recursos de imagen
            long capas = entrada.readLong();
            if (capas < 0) throw new IOException("Longitud de capas invalida");
            saltar(entrada, capas);
            if (entrada.readUnsignedShort() != 0)
                throw new IOException("PSB comprimido no soportado para lectura regional directa");
            long inicio = entrada.getFilePointer();
            long pixeles = Math.multiplyExact((long) ancho, alto);
            long longitud = Math.multiplyExact(pixeles, canales);
            if (entrada.length() - inicio != longitud)
                throw new IOException("Tamano PSB inconsistente o descarga incompleta");
            return new Cabecera(ancho, alto, inicio);
        } catch (ArithmeticException e) {
            throw new IOException("Dimensiones PSB demasiado grandes", e);
        }
    }

    static BufferedImage leer(Path archivo, Cabecera cabecera, RegionImagen region, int submuestreo) throws IOException {
        int anchoSalida = (region.ancho() + submuestreo - 1) / submuestreo;
        int altoSalida = (region.alto() + submuestreo - 1) / submuestreo;
        BufferedImage resultado = new BufferedImage(anchoSalida, altoSalida, BufferedImage.TYPE_INT_RGB);
        long plano = (long) cabecera.ancho() * cabecera.alto();
        byte[][] canales = { new byte[region.ancho()], new byte[region.ancho()], new byte[region.ancho()] };
        try (FileChannel canal = FileChannel.open(archivo)) {
            for (int y = 0; y < altoSalida; y++) {
                long fila = ((long) region.y() + (long) y * submuestreo) * cabecera.ancho() + region.x();
                for (int c = 0; c < 3; c++) {
                    ByteBuffer destino = ByteBuffer.wrap(canales[c]);
                    long posicion = cabecera.inicioPixeles() + c * plano + fila;
                    while (destino.hasRemaining()) {
                        int leidos = canal.read(destino, posicion);
                        if (leidos <= 0) throw new IOException("Lectura PSB incompleta");
                        posicion += leidos;
                    }
                }
                for (int x = 0; x < anchoSalida; x++) {
                    int origen = x * submuestreo;
                    int rgb = (Byte.toUnsignedInt(canales[0][origen]) << 16)
                            | (Byte.toUnsignedInt(canales[1][origen]) << 8)
                            | Byte.toUnsignedInt(canales[2][origen]);
                    resultado.setRGB(x, y, rgb);
                }
            }
        }
        return resultado;
    }

    private static void saltar(RandomAccessFile entrada, long bytes) throws IOException {
        long destino = Math.addExact(entrada.getFilePointer(), bytes);
        if (destino > entrada.length()) throw new IOException("Seccion PSB fuera del archivo");
        entrada.seek(destino);
    }
}
