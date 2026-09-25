package blackwall.sesion;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GestorSesiones {
    private static final long INACTIVIDAD_MAXIMA_MS = 10 * 60 * 1000L;
    private final SecureRandom aleatorio = new SecureRandom();
    private final Map<String, SesionTransferencia> sesiones = new ConcurrentHashMap<>();
    private final int ventanaMaxima;

    public GestorSesiones(int ventanaMaxima) { this.ventanaMaxima = ventanaMaxima; }

    public SesionTransferencia crear() {
        byte[] bytes = new byte[16]; aleatorio.nextBytes(bytes);
        String id = HexFormat.of().formatHex(bytes);
        SesionTransferencia sesion = new SesionTransferencia(id, ventanaMaxima);
        sesiones.put(id, sesion);
        return sesion;
    }

    public SesionTransferencia obtener(String id) {
        SesionTransferencia sesion = sesiones.get(id);
        if (sesion == null) throw new IllegalArgumentException("Sesion desconocida");
        return sesion;
    }

    public void limpiarExpiradas() {
        long ahora = Instant.now().toEpochMilli();
        sesiones.entrySet().removeIf(entry -> entry.getValue().expirada(ahora, INACTIVIDAD_MAXIMA_MS));
    }

    public int cantidad() { return sesiones.size(); }
}
