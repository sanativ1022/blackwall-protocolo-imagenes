package blackwall.imagen;

public record SolicitudVista(long idVista, RegionImagen regionVisible, int nivel,
                            int direccionX, int direccionY, int anchoReceptor,
                            double velocidadX, double velocidadY, double estabilidad) {
    public SolicitudVista(long idVista, RegionImagen regionVisible, int nivel,
                         int direccionX, int direccionY, int anchoReceptor) {
        this(idVista, regionVisible, nivel, direccionX, direccionY, anchoReceptor, 0, 0, 0);
    }
    public SolicitudVista {
        if (idVista <= 0 || regionVisible == null || nivel < 0 || anchoReceptor < 0)
            throw new IllegalArgumentException("Solicitud de vista invalida");
        if (!Double.isFinite(velocidadX) || !Double.isFinite(velocidadY)
                || !Double.isFinite(estabilidad) || estabilidad < 0 || estabilidad > 1)
            throw new IllegalArgumentException("Movimiento invalido");
        direccionX = Integer.compare(direccionX, 0);
        direccionY = Integer.compare(direccionY, 0);
    }
}
