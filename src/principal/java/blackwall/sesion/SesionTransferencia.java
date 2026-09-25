package blackwall.sesion;

import blackwall.congestion.ControlCongestionVivace;
import blackwall.congestion.EstimadorRto;
import blackwall.protocolo.ConfirmacionRecepcion;
import blackwall.protocolo.PaqueteDatos;
import blackwall.protocolo.PrioridadDato;

import java.time.Instant;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SesionTransferencia {
    private final String id;
    private VentanaTransmisionSelectiva ventana;
    private final ControlCongestionVivace congestion;
    private final EstimadorRto estimadorRto = new EstimadorRto();
    private final Set<String> cacheCliente = new HashSet<>();
    private final ArrayDeque<TrabajoMosaico> trabajos = new ArrayDeque<>();
    private final LinkedHashMap<String, byte[]> preparados = new LinkedHashMap<>(16, 0.75f, true);
    private long bytesPreparados;
    private long secuenciaSiguiente = 1;
    private long vistaActual;
    private long leidosDeArchivo, reutilizados, descartados, prediccionesEnviadas;
    private final Set<Long> prediccionesContadas = new HashSet<>();
    private long ultimoAccesoMs = Instant.now().toEpochMilli();

    public SesionTransferencia(String id, int ventanaMaxima) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{16,80}")) throw new IllegalArgumentException("Sesion invalida");
        this.id = id;
        congestion = new ControlCongestionVivace(ventanaMaxima);
        ventana = new VentanaTransmisionSelectiva(congestion, estimadorRto);
    }

    public synchronized void iniciarVista(long idVista, Set<String> contenidoCache) {
        if (idVista <= vistaActual) throw new IllegalArgumentException("Vista obsoleta");
        descartados += trabajos.size() + ventana.paquetesActivos().size();
        ventana.paquetesActivos().forEach(paquete -> guardarPreparado(paquete.idDato(), paquete.contenido()));
        trabajos.clear();
        prediccionesContadas.clear();
        ventana = new VentanaTransmisionSelectiva(congestion, estimadorRto);
        secuenciaSiguiente = 1;
        vistaActual = idVista;
        cacheCliente.clear();
        cacheCliente.addAll(contenidoCache);
        tocar();
    }

    public synchronized boolean agregarDato(String idDato, PrioridadDato prioridad, byte[] contenido) {
        if (cacheCliente.contains(idDato)) return false;
        ventana.encolar(new PaqueteDatos(secuenciaSiguiente++, idDato, vistaActual, prioridad, contenido));
        tocar();
        return true;
    }

    public synchronized int programar(List<TrabajoMosaico> nuevos) {
        int agregados = 0;
        for (TrabajoMosaico trabajo : nuevos) {
            boolean finalEnCache = trabajo.idDato().endsWith(":P")
                    && cacheCliente.contains(trabajo.idDato().substring(0, trabajo.idDato().length() - 1) + "F");
            if (!cacheCliente.contains(trabajo.idDato()) && !finalEnCache) { trabajos.addLast(trabajo); agregados++; }
        }
        tocar(); return agregados;
    }

    public synchronized List<PaqueteDatos> emitirPreparando(long ahoraNanos) throws IOException {
        // Cada respuesta HTTP debe poder terminar tras un solo mosaico.
        // Si se agrupan la previa JPEG y el detalle PNG, el navegador espera
        // todo el lote antes de poder dibujar la previa en una red lenta.
        int cupos = Math.min(ventana.cuposNuevos(), 1);
        while (cupos > 0 && !trabajos.isEmpty()) {
            TrabajoMosaico trabajo = trabajos.peekFirst();
            if (trabajo.prioridad() == PrioridadDato.PREDICCION_PREVIA && !ventana.permitePrediccion()) {
                // Una prediccion opcional no puede dejar la vista pendiente
                // indefinidamente cuando el RTT ya no permite prefetchar.
                do { trabajos.removeFirst(); descartados++; }
                while (!trabajos.isEmpty() && trabajos.peekFirst().prioridad() == PrioridadDato.PREDICCION_PREVIA);
                continue;
            }
            trabajos.removeFirst();
            byte[] contenido = preparados.get(trabajo.idDato());
            if (contenido == null) {
                contenido = trabajo.proveedor().cargar();
                if (contenido.length > 4 * 1024 * 1024) throw new IOException("Mosaico supera el limite de trama");
                guardarPreparado(trabajo.idDato(), contenido);
                leidosDeArchivo++;
            } else {
                reutilizados++;
            }
            ventana.encolar(new PaqueteDatos(secuenciaSiguiente++, trabajo.idDato(), vistaActual,
                    trabajo.prioridad(), contenido));
            cupos--;
        }
        tocar();
        List<PaqueteDatos> enviados = ventana.emitir(ahoraNanos);
        for (PaqueteDatos paquete : enviados) {
            if (paquete.prioridad() == PrioridadDato.PREDICCION_PREVIA
                    && prediccionesContadas.add(paquete.secuencia())) prediccionesEnviadas++;
        }
        return enviados;
    }

    public synchronized List<PaqueteDatos> emitir(long ahoraNanos) { tocar(); return ventana.emitir(ahoraNanos); }

    public synchronized void confirmar(ConfirmacionRecepcion confirmacion, long ahoraNanos) {
        if (confirmacion.idVista() != vistaActual) throw new IllegalArgumentException("ACK de otra vista");
        ventana.procesarConfirmacion(confirmacion, ahoraNanos);
        tocar();
    }

    public synchronized void cancelarVista(long idVista) {
        if (idVista == vistaActual) trabajos.clear();
        ventana.cancelarVista(idVista);
        tocar();
    }

    public String id() { return id; }
    public synchronized long vistaActual() { return vistaActual; }
    public synchronized EstadoVentana estado() {
        EstadoVentana actual = ventana.estado();
        return new EstadoVentana(actual.pendientes() + trabajos.size(), actual.enVuelo(), actual.bytesEnVuelo(),
                actual.confirmados(), actual.retransmisiones(), actual.ventanaCongestion(), actual.ventanaReceptora(),
                actual.rtoMs(), actual.rttMs(), actual.faseCongestion());
    }
    public synchronized double rttMs() { return ventana.estado().rttMs(); }
    public synchronized int prediccionesPendientes() {
        return (int) trabajos.stream().filter(t -> t.prioridad() == PrioridadDato.PREDICCION_PREVIA).count();
    }
    public synchronized long leidosDeArchivo() { return leidosDeArchivo; }
    public synchronized long reutilizados() { return reutilizados; }
    public synchronized long descartados() { return descartados; }
    public synchronized long prediccionesEnviadas() { return prediccionesEnviadas; }
    private void guardarPreparado(String idDato, byte[] contenido) {
        byte[] anterior = preparados.put(idDato, contenido);
        if (anterior != null) bytesPreparados -= anterior.length;
        bytesPreparados += contenido.length;
        while (bytesPreparados > 16L * 1024 * 1024 && !preparados.isEmpty()) {
            Map.Entry<String, byte[]> primero = preparados.entrySet().iterator().next();
            bytesPreparados -= primero.getValue().length;
            preparados.remove(primero.getKey());
        }
    }
    public synchronized boolean expirada(long ahoraMs, long inactividadMaximaMs) { return ahoraMs - ultimoAccesoMs > inactividadMaximaMs; }
    private void tocar() { ultimoAccesoMs = Instant.now().toEpochMilli(); }
}
