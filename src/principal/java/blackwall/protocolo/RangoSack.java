package blackwall.protocolo;

public record RangoSack(long inicio, long fin) {
    public RangoSack {
        if (inicio <= 0 || fin < inicio) throw new IllegalArgumentException("Rango SACK invalido");
    }
    public boolean contiene(long secuencia) { return secuencia >= inicio && secuencia <= fin; }
}
