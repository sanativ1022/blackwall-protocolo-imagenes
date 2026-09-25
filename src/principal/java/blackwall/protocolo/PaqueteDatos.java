package blackwall.protocolo;

import java.util.Arrays;
import java.util.Objects;

public record PaqueteDatos(long secuencia, String idDato, long idVista, PrioridadDato prioridad, byte[] contenido) {
    public PaqueteDatos {
        if (secuencia <= 0) throw new IllegalArgumentException("La secuencia debe ser positiva");
        if (idVista <= 0) throw new IllegalArgumentException("La vista debe ser positiva");
        Objects.requireNonNull(idDato, "idDato");
        Objects.requireNonNull(prioridad, "prioridad");
        contenido = Arrays.copyOf(Objects.requireNonNull(contenido, "contenido"), contenido.length);
        if (contenido.length == 0) throw new IllegalArgumentException("El paquete no puede estar vacio");
    }

    @Override public byte[] contenido() { return Arrays.copyOf(contenido, contenido.length); }
    public int bytes() { return contenido.length; }
}
