package blackwall.servidor;

import blackwall.imagen.*;
import blackwall.protocolo.*;
import blackwall.sesion.EstadoVentana;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;

public final class ServidorHttpConcurrente implements AutoCloseable {
    private final HttpServer servidor;
    private final ServicioProtocoloImagenes servicio;
    private final Path carpetaInterfaz;
    public ServidorHttpConcurrente(InetSocketAddress direccion, ServicioProtocoloImagenes servicio) throws IOException {
        this(direccion, servicio, null);
    }
    public ServidorHttpConcurrente(InetSocketAddress direccion, ServicioProtocoloImagenes servicio, Path carpetaInterfaz) throws IOException {
        this.servicio = servicio;
        this.carpetaInterfaz = carpetaInterfaz == null ? null : carpetaInterfaz.toAbsolutePath().normalize();
        servidor = HttpServer.create(direccion, 128);
        servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        servidor.createContext("/salud", this::salud);
        servidor.createContext("/protocolo/sesiones", this::sesiones);
        servidor.createContext("/protocolo/imagenes", this::imagenes);
        servidor.createContext("/protocolo/vistas", this::vistas);
        servidor.createContext("/protocolo/lotes", this::lotes);
        servidor.createContext("/protocolo/estado", this::estado);
        servidor.createContext("/", this::archivoInterfaz);
    }
    public void iniciar() { servidor.start(); }
    public int puerto() { return servidor.getAddress().getPort(); }
    @Override public void close() { servidor.stop(0); servicio.close(); }

    private void salud(HttpExchange e) throws IOException { responderTexto(e, 200, "DISPONIBLE"); }
    private void sesiones(HttpExchange e) throws IOException {
        if (!e.getRequestMethod().equals("POST")) { responderTexto(e, 405, "Metodo no permitido"); return; }
        responderTexto(e, 201, "sesion=" + servicio.crearSesion());
    }
    private void imagenes(HttpExchange e) throws IOException {
        StringBuilder texto = new StringBuilder();
        for (DescriptorImagen i : servicio.imagenes()) texto.append(i.id()).append('|').append(i.ancho()).append('|')
                .append(i.alto()).append('|').append(i.nivelMaximo()).append('|').append(i.formato()).append('\n');
        responderTexto(e, 200, texto.toString());
    }
    private void vistas(HttpExchange e) throws IOException {
        ejecutar(e, () -> {
            ParametrosPeticion p = cuerpo(e);
            SolicitudVista vista = new SolicitudVista(p.largo("vista", -1),
                    new RegionImagen(p.largo("x", 0), p.largo("y", 0), p.entero("ancho", -1), p.entero("alto", -1)),
                    p.entero("nivel", -1), p.entero("direccionX", 0), p.entero("direccionY", 0), p.entero("ventana", 16),
                    p.decimal("velocidadX", 0), p.decimal("velocidadY", 0), p.decimal("estabilidad", 0));
            int agregados = servicio.iniciarVista(p.requerido("sesion"), p.requerido("imagen"), vista, p.conjunto("cache"));
            responderTexto(e, 200, "estado=ACEPTADA&mosaicos=" + agregados);
        });
    }
    private void lotes(HttpExchange e) throws IOException {
        ejecutar(e, () -> {
            ParametrosPeticion p = cuerpo(e); String id = p.requerido("sesion");
            long vista = p.largo("vista", -1), acumulado = p.largo("ack", 0);
            List<RangoSack> sacks = new ArrayList<>();
            String textoSack = p.opcional("sack", "");
            if (!textoSack.isBlank()) for (String rango : textoSack.split(",")) {
                String[] extremos = rango.split("-"); sacks.add(new RangoSack(Long.parseLong(extremos[0]), Long.parseLong(extremos[extremos.length - 1])));
            }
            ConfirmacionRecepcion ack = vista < 0 ? null : new ConfirmacionRecepcion(vista, acumulado, sacks, p.entero("ventana", 16), p.largo("demoraAck", 0));
            byte[] respuesta = servicio.confirmarYEmitir(id, ack);
            e.getResponseHeaders().set("X-Estado-Protocolo", estadoTexto(id));
            responder(e, 200, "application/octet-stream", respuesta);
        });
    }
    private void estado(HttpExchange e) throws IOException {
        ejecutar(e, () -> {
            String id = new ParametrosPeticion(e.getRequestURI().getRawQuery()).requerido("sesion");
            responderTexto(e, 200, estadoTexto(id));
        });
    }
    private String estadoTexto(String id) {
        EstadoVentana s = servicio.estado(id);
        return "pendientes=" + s.pendientes() + "&enVuelo=" + s.enVuelo() + "&bytesEnVuelo=" + s.bytesEnVuelo()
                + "&confirmados=" + s.confirmados() + "&retransmisiones=" + s.retransmisiones() + "&cwnd=" + s.ventanaCongestion()
                + "&rwnd=" + s.ventanaReceptora() + "&rtoMs=" + s.rtoMs() + "&rttMs=" + s.rttMs() + "&fase=" + s.faseCongestion()
                + "&vista=" + servicio.vistaActual(id) + "&prediccionesPendientes=" + servicio.prediccionesPendientes(id)
                + "&lecturas=" + servicio.sesion(id).leidosDeArchivo() + "&reutilizados=" + servicio.sesion(id).reutilizados()
                + "&descartados=" + servicio.sesion(id).descartados() + "&prediccionesEnviadas=" + servicio.sesion(id).prediccionesEnviadas();
    }
    private void archivoInterfaz(HttpExchange e) throws IOException {
        if (carpetaInterfaz == null) { responderTexto(e, 404, "Interfaz no configurada"); return; }
        String ruta = e.getRequestURI().getPath();
        if (ruta.equals("/")) ruta = "/interfaz.html";
        Path archivo = carpetaInterfaz.resolve(ruta.substring(1)).normalize();
        if (!archivo.startsWith(carpetaInterfaz) || !Files.isRegularFile(archivo)) { responderTexto(e, 404, "Archivo no encontrado"); return; }
        String nombre = archivo.getFileName().toString();
        String tipo = nombre.endsWith(".html") ? "text/html; charset=utf-8" : (nombre.endsWith(".js") || nombre.endsWith(".mjs")) ? "text/javascript; charset=utf-8"
                : nombre.endsWith(".css") ? "text/css; charset=utf-8" : "application/octet-stream";
        responder(e, 200, tipo, Files.readAllBytes(archivo));
    }
    private ParametrosPeticion cuerpo(HttpExchange e) throws IOException {
        if (!e.getRequestMethod().equals("POST")) throw new IllegalArgumentException("Se requiere POST");
        byte[] bytes = e.getRequestBody().readNBytes(1024 * 1024 + 1);
        if (bytes.length > 1024 * 1024) throw new IllegalArgumentException("Peticion demasiado grande");
        return new ParametrosPeticion(new String(bytes, StandardCharsets.UTF_8));
    }
    private void ejecutar(HttpExchange e, Accion accion) throws IOException {
        try { accion.ejecutar(); } catch (IllegalArgumentException ex) { responderTexto(e, 400, "error=" + ex.getMessage()); }
        catch (Exception ex) { responderTexto(e, 500, "error=" + ex.getClass().getSimpleName() + ":" + ex.getMessage()); }
    }
    private void responderTexto(HttpExchange e, int codigo, String texto) throws IOException { responder(e, codigo, "text/plain; charset=utf-8", texto.getBytes(StandardCharsets.UTF_8)); }
    private void responder(HttpExchange e, int codigo, String tipo, byte[] contenido) throws IOException {
        e.getResponseHeaders().set("Content-Type", tipo); e.getResponseHeaders().set("Cache-Control", "no-store");
        e.sendResponseHeaders(codigo, contenido.length); try (OutputStream salida = e.getResponseBody()) { salida.write(contenido); }
    }
    @FunctionalInterface private interface Accion { void ejecutar() throws Exception; }
}
