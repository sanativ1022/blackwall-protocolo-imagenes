package blackwall.congestion;

/**
 * Adaptacion de PCC Vivace a la ventana de mosaicos de BlackWall.
 * Los ACK forman intervalos de observacion; su utilidad combina tasa,
 * gradiente de RTT y perdidas. No pretende implementar el transporte TCP.
 */
public final class ControlCongestionVivace {
    public enum Fase { ARRANQUE_LENTO, CONTROL_VIVACE, RECUPERACION }

    private final double ventanaMaxima;
    private double ventanaCongestion = 2.0;
    private double rttMinimoMs = Double.POSITIVE_INFINITY;
    private double rttActualMs;
    private double rttInicioIntervaloMs;
    private double utilidadAnterior = Double.NaN;
    private double paso = 0.5;
    private int direccion = 1;
    private int confirmadosIntervalo;
    private int perdidasIntervalo;
    private Fase fase = Fase.ARRANQUE_LENTO;

    public ControlCongestionVivace(int ventanaMaxima) {
        if (ventanaMaxima < 2) throw new IllegalArgumentException("Ventana maxima invalida");
        this.ventanaMaxima = ventanaMaxima;
    }

    public void alConfirmar(long rttMs, int paquetesConfirmados) {
        if (rttMs > 0) {
            if (rttInicioIntervaloMs == 0) rttInicioIntervaloMs = rttMs;
            rttActualMs = rttMs;
            rttMinimoMs = Math.min(rttMinimoMs, rttMs);
        }
        int nuevos = Math.max(0, paquetesConfirmados);
        if (fase == Fase.ARRANQUE_LENTO) {
            if (rttActualMs > rttMinimoMs * 1.25) {
                fase = Fase.CONTROL_VIVACE;
                ventanaCongestion = Math.max(2.0, ventanaCongestion * 0.85);
                reiniciarIntervalo();
            } else {
                ventanaCongestion = limitar(ventanaCongestion + nuevos, 2.0, ventanaMaxima);
                return;
            }
        }
        if (fase == Fase.RECUPERACION) fase = Fase.CONTROL_VIVACE;
        confirmadosIntervalo += nuevos;
        if (confirmadosIntervalo < Math.max(2, Math.ceil(ventanaCongestion))) return;

        double rtt = Math.max(1.0, rttActualMs);
        double tasa = ventanaCongestion / rtt;
        double gradiente = Math.max(0, (rttActualMs - rttInicioIntervaloMs)
                / Math.max(1.0, rttInicioIntervaloMs));
        double perdida = (double) perdidasIntervalo / Math.max(1, confirmadosIntervalo + perdidasIntervalo);
        double utilidad = Math.pow(tasa, 0.8) - 0.6 * tasa * gradiente - 2.0 * tasa * perdida;
        if (Double.isFinite(utilidadAnterior)) {
            if (utilidad < utilidadAnterior) {
                direccion = -direccion;
                paso = Math.max(0.25, paso / 2.0);
            } else {
                paso = Math.min(Math.max(0.5, ventanaCongestion * 0.25), paso * 1.1);
            }
        } else if (gradiente > 0.1 || perdida > 0) {
            direccion = -1;
        }
        utilidadAnterior = utilidad;
        ventanaCongestion = limitar(ventanaCongestion + direccion * paso, 2.0, ventanaMaxima);
        reiniciarIntervalo();
    }

    public void alDetectarPerdida() {
        perdidasIntervalo++;
        ventanaCongestion = limitar(ventanaCongestion * 0.7, 2.0, ventanaMaxima);
        direccion = -1;
        fase = Fase.RECUPERACION;
    }

    private void reiniciarIntervalo() {
        confirmadosIntervalo = 0;
        perdidasIntervalo = 0;
        rttInicioIntervaloMs = rttActualMs;
    }

    public int limitePaquetes(int ventanaReceptora) {
        return Math.max(0, Math.min(ventanaReceptora, (int) Math.floor(ventanaCongestion)));
    }

    public double ventanaCongestion() { return ventanaCongestion; }
    public double rttMinimoMs() { return Double.isFinite(rttMinimoMs) ? rttMinimoMs : 0; }
    public double rttActualMs() { return rttActualMs; }
    public Fase fase() { return fase; }
    private double limitar(double valor, double minimo, double maximo) { return Math.max(minimo, Math.min(maximo, valor)); }
}
