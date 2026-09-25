package blackwall.sesion;

import blackwall.congestion.ControlCongestionVivace;
import blackwall.congestion.EstimadorRto;
import blackwall.protocolo.ConfirmacionRecepcion;
import blackwall.protocolo.PaqueteDatos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class VentanaTransmisionSelectiva {
    private static final long MAXIMO_BYTES_EN_VUELO = 16L * 1024 * 1024;
    private static final long MAXIMO_BYTES_POR_PAQUETE = 4L * 1024 * 1024;
    private final PriorityQueue<PaqueteDatos> pendientes = new PriorityQueue<>(Comparator
            .comparingInt((PaqueteDatos paquete) -> paquete.prioridad().orden())
            .thenComparingLong(PaqueteDatos::secuencia));
    private final Map<Long, Envio> enviados = new HashMap<>();
    private final EstimadorRto estimadorRto;
    private final ControlCongestionVivace congestion;
    private int ventanaReceptora = 32;
    private long totalConfirmados;
    private long totalRetransmisiones;
    private long ultimoEnvioConfirmadoNanos = -1;
    private long instanteUltimaConfirmacionNanos = -1;
    private long mayorSecuenciaConfirmada;

    public VentanaTransmisionSelectiva(int ventanaMaxima) {
        this(new ControlCongestionVivace(ventanaMaxima), new EstimadorRto());
    }

    public VentanaTransmisionSelectiva(ControlCongestionVivace congestion, EstimadorRto estimadorRto) {
        this.congestion = congestion;
        this.estimadorRto = estimadorRto;
    }

    public synchronized void encolar(PaqueteDatos paquete) {
        if (enviados.containsKey(paquete.secuencia()) || pendientes.stream().anyMatch(p -> p.secuencia() == paquete.secuencia())) {
            throw new IllegalArgumentException("Secuencia duplicada");
        }
        pendientes.add(paquete);
    }

    public synchronized List<PaqueteDatos> emitir(long ahoraNanos) {
        List<PaqueteDatos> salida = new ArrayList<>();
        long rtoNanos = estimadorRto.rtoMs() * 1_000_000L;
        int limite = congestion.limitePaquetes(ventanaReceptora);
        if (limite == 0) return List.of();

        for (Envio envio : enviados.values().stream().sorted(Comparator.comparingLong(e -> e.paquete.secuencia())).toList()) {
            boolean timeout = ahoraNanos - envio.ultimoEnvioNanos >= rtoNanos;
            // RACK: un ACK de un envio posterior permite inferir la perdida
            // tras una pequena ventana temporal de reordenamiento.
            long reordenamientoNanos = Math.max(1, Math.round(
                    Math.max(1, congestion.rttMinimoMs()) * 250_000.0));
            boolean rack = ultimoEnvioConfirmadoNanos >= envio.ultimoEnvioNanos
                    && mayorSecuenciaConfirmada > envio.paquete.secuencia()
                    && instanteUltimaConfirmacionNanos >= 0
                    && ahoraNanos - instanteUltimaConfirmacionNanos
                    + ultimoEnvioConfirmadoNanos - envio.ultimoEnvioNanos >= reordenamientoNanos;
            long rttNanos = Math.max(1, Math.round(estimadorRto.rttSuavizadoMs() * 1_000_000.0));
            long ptoNanos = Math.min(rtoNanos - 1, 2 * rttNanos);
            boolean sondaCola = !timeout && !rack && enviados.size() == 1 && pendientes.isEmpty()
                    && !envio.sondaEnviada && estimadorRto.rttSuavizadoMs() > 0
                    && ahoraNanos - envio.ultimoEnvioNanos >= ptoNanos;
            if (!timeout && !rack && !sondaCola) continue;
            if (timeout) estimadorRto.registrarTimeout();
            if (timeout || rack) congestion.alDetectarPerdida();
            envio.reenviar(ahoraNanos);
            totalRetransmisiones++;
            salida.add(envio.paquete);
            if (salida.size() >= Math.max(1, limite)) return salida;
        }

        long bytesEnVuelo = enviados.values().stream().mapToLong(e -> e.paquete.bytes()).sum();
        int cupos = Math.max(0, limite - enviados.size());
        while (cupos-- > 0 && !pendientes.isEmpty()) {
            if (bytesEnVuelo + pendientes.peek().bytes() > MAXIMO_BYTES_EN_VUELO) break;
            PaqueteDatos paquete = pendientes.remove();
            enviados.put(paquete.secuencia(), new Envio(paquete, ahoraNanos));
            bytesEnVuelo += paquete.bytes();
            salida.add(paquete);
        }
        return List.copyOf(salida);
    }

    public synchronized int cuposNuevos() {
        long ocupados = enviados.values().stream().mapToLong(e -> e.paquete.bytes()).sum()
                + pendientes.stream().mapToLong(PaqueteDatos::bytes).sum();
        int cuposBytes = (int) Math.max(0, (MAXIMO_BYTES_EN_VUELO - ocupados) / MAXIMO_BYTES_POR_PAQUETE);
        return Math.max(0, Math.min(cuposBytes, congestion.limitePaquetes(ventanaReceptora) - enviados.size() - pendientes.size()));
    }

    public synchronized boolean permitePrediccion() {
        return enviados.isEmpty() && pendientes.isEmpty() && ventanaReceptora > 0
                && congestion.ventanaCongestion() >= 4 && congestion.rttMinimoMs() > 0
                && congestion.rttActualMs() <= congestion.rttMinimoMs() * 1.25;
    }

    public synchronized void procesarConfirmacion(ConfirmacionRecepcion confirmacion, long ahoraNanos) {
        ventanaReceptora = confirmacion.ventanaReceptoraPaquetes();
        int nuevos = 0;
        Iterator<Map.Entry<Long, Envio>> iterator = enviados.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Envio> entry = iterator.next();
            Envio envio = entry.getValue();
            if (confirmacion.confirma(entry.getKey())) {
                if (entry.getKey() > mayorSecuenciaConfirmada) {
                    mayorSecuenciaConfirmada = entry.getKey();
                    ultimoEnvioConfirmadoNanos = envio.ultimoEnvioNanos;
                    instanteUltimaConfirmacionNanos = ahoraNanos;
                }
                if (envio.intentos == 1) {
                    long rttMs = Math.max(1, (ahoraNanos - envio.ultimoEnvioNanos) / 1_000_000L);
                    estimadorRto.registrarMuestra(rttMs);
                    congestion.alConfirmar(rttMs, 1);
                } else {
                    congestion.alConfirmar(Math.round(congestion.rttActualMs()), 1);
                }
                iterator.remove();
                nuevos++;
            }
        }
        totalConfirmados += nuevos;
    }

    public synchronized void cancelarVista(long idVista) {
        pendientes.removeIf(paquete -> paquete.idVista() == idVista);
        enviados.entrySet().removeIf(entry -> entry.getValue().paquete.idVista() == idVista);
    }

    public synchronized boolean terminada() { return pendientes.isEmpty() && enviados.isEmpty(); }

    public synchronized List<PaqueteDatos> paquetesActivos() {
        List<PaqueteDatos> activos = new ArrayList<>(pendientes);
        enviados.values().forEach(envio -> activos.add(envio.paquete));
        return List.copyOf(activos);
    }

    public synchronized EstadoVentana estado() {
        long bytes = enviados.values().stream().mapToLong(envio -> envio.paquete.bytes()).sum();
        return new EstadoVentana(pendientes.size(), enviados.size(), bytes, totalConfirmados,
                totalRetransmisiones, congestion.ventanaCongestion(), ventanaReceptora,
                estimadorRto.rtoMs(), estimadorRto.rttSuavizadoMs(), congestion.fase().name());
    }

    private static final class Envio {
        final PaqueteDatos paquete;
        long ultimoEnvioNanos;
        int intentos = 1;
        boolean sondaEnviada;

        Envio(PaqueteDatos paquete, long ahoraNanos) {
            this.paquete = paquete;
            ultimoEnvioNanos = ahoraNanos;
        }

        void reenviar(long ahoraNanos) {
            ultimoEnvioNanos = ahoraNanos;
            intentos++;
            sondaEnviada = true;
        }
    }
}
