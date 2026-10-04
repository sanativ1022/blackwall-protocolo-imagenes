package blackwall.sesion;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class GestorSesiones implements AutoCloseable {
    private final SecureRandom aleatorio = new SecureRandom();
    private final Map<String, SesionTransferencia> sesiones = new ConcurrentHashMap<>();
    private final int ventanaMaxima;
    private final long inactividadMaximaMs;
    private final ScheduledExecutorService limpieza = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread hilo = new Thread(runnable, "blackwall-limpieza-sesiones");
        hilo.setDaemon(true);
        return hilo;
    });

    public GestorSesiones(int ventanaMaxima) {
        this(ventanaMaxima, TimeUnit.MINUTES.toMillis(10), TimeUnit.MINUTES.toMillis(1));
    }

    public GestorSesiones(int ventanaMaxima, long inactividadMaximaMs, long periodoLimpiezaMs) {
        if (inactividadMaximaMs <= 0 || periodoLimpiezaMs <= 0) throw new IllegalArgumentException("Plazos invalidos");
        this.ventanaMaxima = ventanaMaxima;
        this.inactividadMaximaMs = inactividadMaximaMs;
        limpieza.scheduleWithFixedDelay(this::limpiarExpiradas, periodoLimpiezaMs, periodoLimpiezaMs, TimeUnit.MILLISECONDS);
    }

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
        sesiones.entrySet().removeIf(entry -> entry.getValue().expirada(ahora, inactividadMaximaMs));
    }

    public int cantidad() { return sesiones.size(); }
    @Override public void close() { limpieza.shutdownNow(); }
}
