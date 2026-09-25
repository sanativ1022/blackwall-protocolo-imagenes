package blackwall.protocolo;

public enum PrioridadDato {
    VISIBLE_PREVIA(0),
    VISIBLE_FINAL(1),
    PREDICCION_PREVIA(2),
    PREDICCION_FINAL(3);

    private final int orden;

    PrioridadDato(int orden) { this.orden = orden; }
    public int orden() { return orden; }
}
