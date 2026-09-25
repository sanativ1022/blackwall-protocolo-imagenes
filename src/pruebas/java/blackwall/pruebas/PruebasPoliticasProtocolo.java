package blackwall.pruebas;

import blackwall.congestion.ControlCongestionVivace;
import blackwall.protocolo.*;
import blackwall.sesion.VentanaTransmisionSelectiva;
import java.util.List;

public final class PruebasPoliticasProtocolo {
    public static void main(String[] args) {
        ControlCongestionVivace control = new ControlCongestionVivace(64);
        control.alConfirmar(20, 5);
        double antes = control.ventanaCongestion();
        control.alConfirmar(80, 8);
        verificar(control.fase() == ControlCongestionVivace.Fase.CONTROL_VIVACE, "el lag abandona arranque lento");
        verificar(control.ventanaCongestion() < antes, "el aumento de cola reduce la ventana");
        double antesDePerdida = control.ventanaCongestion();
        control.alDetectarPerdida();
        verificar(control.fase() == ControlCongestionVivace.Fase.RECUPERACION
                && control.ventanaCongestion() < antesDePerdida, "la perdida reduce la tasa propuesta");
        control.alConfirmar(20, 8);
        verificar(control.fase() == ControlCongestionVivace.Fase.CONTROL_VIVACE,
                "ACK posteriores reanudan la optimizacion por utilidad");
        verificar(control.limitePaquetes(0) == 0, "la ventana receptora cero domina el control de congestion");

        VentanaTransmisionSelectiva ventana = new VentanaTransmisionSelectiva(16);
        ventana.encolar(new PaqueteDatos(1, "x", 1, PrioridadDato.VISIBLE_FINAL, new byte[]{1}));
        ventana.procesarConfirmacion(new ConfirmacionRecepcion(1, 0, List.of(), 0, 0), 1);
        verificar(ventana.emitir(2).isEmpty(), "ventana receptora cero detiene envios");
        System.out.println("[OK] Adaptacion Vivace y control de flujo con ventana cero superadas.");
    }
    private static void verificar(boolean condicion, String mensaje) { if (!condicion) throw new AssertionError(mensaje); }
}
