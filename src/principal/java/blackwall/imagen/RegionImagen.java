package blackwall.imagen;

public record RegionImagen(long x, long y, int ancho, int alto) {
    public RegionImagen {
        if (x < 0 || y < 0 || ancho <= 0 || alto <= 0) throw new IllegalArgumentException("Region invalida");
    }
    public long derecha() { return Math.addExact(x, ancho); }
    public long abajo() { return Math.addExact(y, alto); }
    public boolean intersecta(RegionImagen otra) {
        return x < otra.derecha() && derecha() > otra.x && y < otra.abajo() && abajo() > otra.y;
    }
}
