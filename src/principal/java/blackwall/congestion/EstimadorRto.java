package blackwall.congestion;

public final class EstimadorRto {
    private static final double ALFA = 1.0 / 8.0;
    private static final double BETA = 1.0 / 4.0;
    private double rttSuavizadoMs;
    private double variacionRttMs;
    private long rtoMs = 1_000;

    public void registrarMuestra(long rttMs) {
        if (rttMs <= 0) return;
        if (rttSuavizadoMs == 0) {
            rttSuavizadoMs = rttMs;
            variacionRttMs = rttMs / 2.0;
        } else {
            variacionRttMs = (1 - BETA) * variacionRttMs + BETA * Math.abs(rttSuavizadoMs - rttMs);
            rttSuavizadoMs = (1 - ALFA) * rttSuavizadoMs + ALFA * rttMs;
        }
        rtoMs = limitar(Math.round(rttSuavizadoMs + Math.max(10, 4 * variacionRttMs)), 200, 60_000);
    }

    public void registrarTimeout() { rtoMs = limitar(rtoMs * 2, 200, 60_000); }
    public long rtoMs() { return rtoMs; }
    public double rttSuavizadoMs() { return rttSuavizadoMs; }
    public double variacionRttMs() { return variacionRttMs; }
    private static long limitar(long valor, long minimo, long maximo) { return Math.max(minimo, Math.min(maximo, valor)); }
}
