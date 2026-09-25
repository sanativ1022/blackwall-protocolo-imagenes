package blackwall.pruebas;

import blackwall.protocolo.CodificadorTramas;
import blackwall.protocolo.ConfirmacionRecepcion;
import blackwall.protocolo.PaqueteDatos;
import blackwall.protocolo.PrioridadDato;
import blackwall.protocolo.RangoSack;
import blackwall.sesion.GestorSesiones;
import blackwall.sesion.SesionTransferencia;
import blackwall.sesion.VentanaTransmisionSelectiva;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class PruebasNucleoProtocolo {
    private int superadas;

    public static void main(String[] args) throws Exception { new PruebasNucleoProtocolo().ejecutar(); }

    private void ejecutar() throws Exception {
        probarTramaYDeteccionCorrupcion();
        probarVentanaYControlReceptor();
        probarRackYReordenamiento();
        probarSondaTlp();
        probarTimeoutYReduccionCongestion();
        probarCacheYCancelacion();
        probarSesionesConcurrentes();
        System.out.printf("[OK] %d/7 grupos del nucleo superados.%n", superadas);
    }

    private void probarTramaYDeteccionCorrupcion() throws Exception {
        CodificadorTramas codificador = new CodificadorTramas();
        PaqueteDatos original = paquete(1, 1, PrioridadDato.VISIBLE_PREVIA);
        byte[] trama = codificador.codificar(original);
        PaqueteDatos recuperado = codificador.decodificar(trama);
        verificar(recuperado.secuencia() == 1 && recuperado.idDato().equals(original.idDato()), "codificacion reversible");
        trama[trama.length - 1] ^= 1;
        boolean detectada = false;
        try { codificador.decodificar(trama); } catch (IOException esperada) { detectada = true; }
        verificar(detectada, "CRC detecta corrupcion");
        superadas++;
    }

    private void probarVentanaYControlReceptor() {
        VentanaTransmisionSelectiva ventana = nuevaVentana(8, 1);
        List<PaqueteDatos> inicial = ventana.emitir(1_000_000_000L);
        verificar(inicial.size() == 2, "arranque lento inicia con dos paquetes");
        ventana.procesarConfirmacion(new ConfirmacionRecepcion(1, 1, List.of(), 1, 0), 1_050_000_000L);
        List<PaqueteDatos> siguiente = ventana.emitir(1_060_000_000L);
        verificar(siguiente.isEmpty(), "ventana receptora limita datos en vuelo");
        ventana.procesarConfirmacion(new ConfirmacionRecepcion(1, 2, List.of(), 8, 0), 1_100_000_000L);
        verificar(ventana.emitir(1_110_000_000L).size() >= 2, "ventana crece al confirmar");
        superadas++;
    }

    private void probarRackYReordenamiento() {
        VentanaTransmisionSelectiva ventana = nuevaVentana(2, 1);
        List<PaqueteDatos> lote = ventana.emitir(2_000_000_000L);
        verificar(lote.size() == 2, "lote inicial");
        ConfirmacionRecepcion hueco = new ConfirmacionRecepcion(1, 0, List.of(new RangoSack(2, 2)), 8, 0);
        ventana.procesarConfirmacion(hueco, 2_020_000_000L);
        verificar(ventana.emitir(2_024_000_000L).isEmpty(), "RACK tolera reordenamiento breve");
        List<PaqueteDatos> retransmitidos = ventana.emitir(2_025_000_000L);
        verificar(retransmitidos.stream().map(PaqueteDatos::secuencia).toList().equals(List.of(1L)),
                "RACK retransmite solo el hueco tras la ventana temporal");
        verificar(ventana.estado().retransmisiones() == 1, "RACK cuenta una retransmision");
        superadas++;
    }

    private void probarSondaTlp() {
        VentanaTransmisionSelectiva ventana = nuevaVentana(2, 1);
        ventana.emitir(1_000_000_000L);
        ventana.procesarConfirmacion(new ConfirmacionRecepcion(1, 1, List.of(), 8, 0), 1_050_000_000L);
        double congestionAntes = ventana.estado().ventanaCongestion();
        verificar(ventana.emitir(1_099_000_000L).isEmpty(), "TLP espera dos RTT");
        verificar(ventana.emitir(1_100_000_000L).stream().map(PaqueteDatos::secuencia)
                .toList().equals(List.of(2L)), "TLP sondea el ultimo bloque antes del RTO");
        verificar(ventana.estado().ventanaCongestion() == congestionAntes,
                "una sonda no declara congestion prematuramente");
        verificar(ventana.emitir(1_101_000_000L).isEmpty(), "TLP no duplica la sonda");
        superadas++;
    }

    private void probarTimeoutYReduccionCongestion() {
        VentanaTransmisionSelectiva ventana = nuevaVentana(3, 1);
        ventana.emitir(3_000_000_000L);
        double antes = ventana.estado().ventanaCongestion();
        List<PaqueteDatos> retransmitidos = ventana.emitir(4_100_000_000L);
        verificar(!retransmitidos.isEmpty(), "timeout provoca retransmision");
        verificar(ventana.estado().ventanaCongestion() <= antes, "perdida reduce ventana");
        verificar(ventana.estado().rtoMs() >= 2_000, "timeout aplica backoff al RTO");
        superadas++;
    }

    private void probarCacheYCancelacion() {
        SesionTransferencia sesion = new SesionTransferencia("0123456789abcdef", 16);
        sesion.iniciarVista(1, Set.of("dato-cache"));
        verificar(!sesion.agregarDato("dato-cache", PrioridadDato.VISIBLE_FINAL, new byte[]{1}), "cache evita reenvio");
        verificar(sesion.agregarDato("dato-nuevo", PrioridadDato.VISIBLE_FINAL, new byte[]{2}), "dato nuevo se encola");
        sesion.cancelarVista(1);
        verificar(sesion.estado().pendientes() == 0 && sesion.estado().enVuelo() == 0, "cancelar elimina trabajo obsoleto");
        superadas++;
    }

    private void probarSesionesConcurrentes() {
        GestorSesiones gestor = new GestorSesiones(32);
        List<CompletableFuture<String>> tareas = new ArrayList<>();
        for (int i = 0; i < 50; i++) tareas.add(CompletableFuture.supplyAsync(() -> gestor.crear().id()));
        CompletableFuture.allOf(tareas.toArray(CompletableFuture[]::new)).join();
        Set<String> ids = new HashSet<>();
        tareas.forEach(tarea -> ids.add(tarea.join()));
        verificar(ids.size() == 50 && gestor.cantidad() == 50, "sesiones concurrentes independientes");
        superadas++;
    }

    private VentanaTransmisionSelectiva nuevaVentana(int paquetes, long vista) {
        VentanaTransmisionSelectiva ventana = new VentanaTransmisionSelectiva(64);
        for (int i = 1; i <= paquetes; i++) ventana.encolar(paquete(i, vista, PrioridadDato.VISIBLE_FINAL));
        return ventana;
    }

    private PaqueteDatos paquete(long secuencia, long vista, PrioridadDato prioridad) {
        return new PaqueteDatos(secuencia, "dato-" + secuencia, vista, prioridad, new byte[]{(byte) secuencia, 2, 3});
    }

    private static void verificar(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }
}
