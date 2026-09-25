package blackwall.protocolo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

public final class CodificadorTramas {
    private static final int MAGIA = 0x42574931; // BWI1
    private static final int VERSION = 1;
    private static final int MAXIMO_ID = 512;
    private static final int MAXIMO_CONTENIDO = 4 * 1024 * 1024;

    public byte[] codificar(PaqueteDatos paquete) throws IOException {
        byte[] id = paquete.idDato().getBytes(StandardCharsets.UTF_8);
        byte[] contenido = paquete.contenido();
        if (id.length == 0 || id.length > MAXIMO_ID || contenido.length > MAXIMO_CONTENIDO) {
            throw new IOException("Paquete fuera de limites");
        }
        CRC32 crc = new CRC32();
        crc.update(contenido);
        ByteArrayOutputStream salida = new ByteArrayOutputStream(40 + id.length + contenido.length);
        try (DataOutputStream datos = new DataOutputStream(salida)) {
            datos.writeInt(MAGIA);
            datos.writeByte(VERSION);
            datos.writeByte(paquete.prioridad().ordinal());
            datos.writeLong(paquete.secuencia());
            datos.writeLong(paquete.idVista());
            datos.writeShort(id.length);
            datos.writeInt(contenido.length);
            datos.writeInt((int) crc.getValue());
            datos.write(id);
            datos.write(contenido);
        }
        return salida.toByteArray();
    }

    public PaqueteDatos decodificar(byte[] trama) throws IOException {
        try (DataInputStream datos = new DataInputStream(new ByteArrayInputStream(trama))) {
            if (datos.readInt() != MAGIA || datos.readUnsignedByte() != VERSION) throw new IOException("Trama incompatible");
            int prioridad = datos.readUnsignedByte();
            long secuencia = datos.readLong();
            long vista = datos.readLong();
            int largoId = datos.readUnsignedShort();
            int largoContenido = datos.readInt();
            long crcEsperado = Integer.toUnsignedLong(datos.readInt());
            if (prioridad >= PrioridadDato.values().length || largoId <= 0 || largoId > MAXIMO_ID
                    || largoContenido <= 0 || largoContenido > MAXIMO_CONTENIDO
                    || datos.available() != largoId + largoContenido) throw new IOException("Longitudes de trama invalidas");
            String id = new String(datos.readNBytes(largoId), StandardCharsets.UTF_8);
            byte[] contenido = datos.readNBytes(largoContenido);
            CRC32 crc = new CRC32(); crc.update(contenido);
            if (crc.getValue() != crcEsperado) throw new IOException("CRC incorrecto");
            return new PaqueteDatos(secuencia, id, vista, PrioridadDato.values()[prioridad], contenido);
        }
    }
}
