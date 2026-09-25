package blackwall.pruebas;

import blackwall.imagen.*;
import blackwall.protocolo.CodificadorTramas;
import blackwall.servidor.*;
import blackwall.sesion.GestorSesiones;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class PruebasIntegracionServidor {
    public static void main(String[] args) throws Exception {
        var temporal = Files.createTempFile("blackwall-http-", ".png");
        try {
            BufferedImage imagen = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++) imagen.setRGB(x, y, (x << 16) ^ y);
            ImageIO.write(imagen, "png", temporal.toFile());
            LectorImagenRegional lector = new LectorImagenRegional(512L * 512L);
            CatalogoImagenes catalogo = new CatalogoImagenes(lector); catalogo.registrar("numeros", temporal, 256);
            var servicio = new ServicioProtocoloImagenes(new GestorSesiones(32), catalogo, lector);
            try (var servidor = new ServidorHttpConcurrente(new InetSocketAddress("127.0.0.1", 0), servicio, java.nio.file.Path.of("interfaz"))) {
                servidor.iniciar(); String base = "http://127.0.0.1:" + servidor.puerto();
                HttpClient cliente = HttpClient.newHttpClient();
                verificar(get(cliente, base + "/salud").equals("DISPONIBLE"), "servidor responde");
                verificar(get(cliente, base + "/").contains("Visor BlackWall"), "interfaz se sirve desde el mismo origen");
                String sesion = postTexto(cliente, base + "/protocolo/sesiones", "").substring("sesion=".length());
                String vista = "sesion=" + sesion + "&imagen=numeros&vista=1&x=0&y=0&ancho=256&alto=256&nivel=1&ventana=4";
                verificar(postTexto(cliente, base + "/protocolo/vistas", vista).contains("ACEPTADA"), "vista aceptada");
                verificar(get(cliente, base + "/protocolo/estado?sesion=" + sesion).contains("lecturas=0"),
                        "vista programada sin leer imagen por anticipado");
                HttpResponse<byte[]> primerLote = postRespuesta(cliente, base + "/protocolo/lotes", "sesion=" + sesion);
                byte[] lote = primerLote.body();
                verificar(primerLote.headers().firstValue("X-Estado-Protocolo").orElse("").contains("enVuelo=1"),
                        "lote incluye estado sin una solicitud adicional");
                List<Long> secuencias = secuencias(lote);
                verificar(secuencias.equals(List.of(1L)), "primera previa llega en su propia respuesta");
                String estado = get(cliente, base + "/protocolo/estado?sesion=" + sesion);
                verificar(estado.contains("enVuelo=1") && estado.contains("fase=ARRANQUE_LENTO"), "estado observable");
                verificar(estado.contains("lecturas=1"), "solo se preparo el bloque enviado");
                HttpResponse<byte[]> segundoLote = postRespuesta(cliente, base + "/protocolo/lotes",
                        "sesion=" + sesion + "&vista=1&ack=1&ventana=4&demoraAck=2");
                byte[] siguiente = segundoLote.body();
                verificar(secuencias(siguiente).equals(List.of(2L)), "mosaico final se envia en otra respuesta");
                HttpResponse<byte[]> tercerLote = postRespuesta(cliente, base + "/protocolo/lotes",
                        "sesion=" + sesion + "&vista=1&ack=2&ventana=4&demoraAck=2");
                verificar(secuencias(tercerLote.body()).isEmpty(), "ACK final no envia bloques extra");
                verificar(tercerLote.headers().firstValue("X-Estado-Protocolo").orElse("").contains("enVuelo=0"),
                        "ACK final confirma vista terminada en el mismo viaje");

                String mismaRegionEnCache = "sesion=" + sesion
                        + "&imagen=numeros&vista=2&x=0&y=0&ancho=256&alto=256&nivel=1&ventana=4"
                        + "&cache=numeros%3A1%3A0%3A0%3AP%2Cnumeros%3A1%3A0%3A0%3AF";
                verificar(postTexto(cliente, base + "/protocolo/vistas", mismaRegionEnCache).contains("mosaicos=0"),
                        "volver a una region cacheada no programa mosaicos");
                byte[] loteCacheado = postRespuesta(cliente, base + "/protocolo/lotes",
                        "sesion=" + sesion + "&vista=2&ack=0&ventana=4").body();
                verificar(secuencias(loteCacheado).isEmpty(), "region cacheada no retransmite bytes de imagen");
                verificar(get(cliente, base + "/protocolo/estado?sesion=" + sesion).contains("lecturas=2"),
                        "region cacheada no relee la imagen de disco");

                String sesionConPerdida = postTexto(cliente, base + "/protocolo/sesiones", "").substring("sesion=".length());
                String vistaConPerdida = "sesion=" + sesionConPerdida
                        + "&imagen=numeros&vista=1&x=0&y=0&ancho=256&alto=256&nivel=1&ventana=4";
                verificar(postTexto(cliente, base + "/protocolo/vistas", vistaConPerdida).contains("ACEPTADA"),
                        "segunda sesion acepta una vista independiente");
                String rutaLotes = base + "/protocolo/lotes";
                byte[] tramaInicial = postRespuesta(cliente, rutaLotes, "sesion=" + sesionConPerdida).body();
                verificar(secuencias(tramaInicial).equals(List.of(1L)), "primera trama de la segunda sesion");
                byte[] danada = tramaInicial.clone();
                danada[danada.length - 1] ^= 1;
                boolean crcRechazado = false;
                try { secuencias(danada); } catch (IOException esperada) { crcRechazado = true; }
                verificar(crcRechazado, "cliente detecta corrupcion antes de confirmar");
                verificar(secuencias(postRespuesta(cliente, rutaLotes, "sesion=" + sesionConPerdida
                        + "&vista=1&ack=0&ventana=4").body()).equals(List.of(2L)),
                        "segundo mosaico permite informar hueco por SACK");
                String hueco = "sesion=" + sesionConPerdida + "&vista=1&ack=0&sack=2-2&ventana=4";
                List<Long> recuperados = new ArrayList<>();
                for (int intento = 0; intento < 4 && recuperados.isEmpty(); intento++) {
                    recuperados.addAll(secuencias(postRespuesta(cliente, rutaLotes, hueco).body()));
                    if (recuperados.isEmpty()) Thread.sleep(5);
                }
                verificar(recuperados.equals(List.of(1L)),
                        "RACK retransmite solo el mosaico danado tras confirmar el posterior");
                verificar(get(cliente, base + "/protocolo/estado?sesion=" + sesionConPerdida)
                        .contains("retransmisiones=1"), "retransmision visible en el estado");
                postRespuesta(cliente, rutaLotes, "sesion=" + sesionConPerdida + "&vista=1&ack=2&ventana=4");
                verificar(get(cliente, base + "/protocolo/estado?sesion=" + sesionConPerdida)
                        .contains("enVuelo=0"), "sesion recuperada concluye sin datos pendientes");

                List<CompletableFuture<String>> concurrentes = new ArrayList<>();
                for (int i = 0; i < 20; i++) concurrentes.add(cliente.sendAsync(HttpRequest.newBuilder(URI.create(base + "/protocolo/sesiones"))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body));
                CompletableFuture.allOf(concurrentes.toArray(CompletableFuture[]::new)).join();
                verificar(concurrentes.stream().map(CompletableFuture::join).distinct().count() == 20, "clientes concurrentes aislados");

                List<String> otrasSesiones = concurrentes.subList(0, 3).stream()
                        .map(futuro -> futuro.join().substring("sesion=".length())).toList();
                for (int i = 0; i < otrasSesiones.size(); i++) {
                    String otraVista = "sesion=" + otrasSesiones.get(i) + "&imagen=numeros&vista=1&x="
                            + (i * 128) + "&y=0&ancho=128&alto=128&nivel=1&ventana=4";
                    verificar(postTexto(cliente, base + "/protocolo/vistas", otraVista).contains("ACEPTADA"),
                            "vista concurrente aceptada");
                }
                List<CompletableFuture<HttpResponse<byte[]>>> lotesConcurrentes = new ArrayList<>();
                for (String otraSesion : otrasSesiones) {
                    HttpRequest peticion = HttpRequest.newBuilder(URI.create(base + "/protocolo/lotes"))
                            .POST(HttpRequest.BodyPublishers.ofString("sesion=" + otraSesion)).build();
                    lotesConcurrentes.add(cliente.sendAsync(peticion, HttpResponse.BodyHandlers.ofByteArray()));
                }
                CompletableFuture.allOf(lotesConcurrentes.toArray(CompletableFuture[]::new)).join();
                for (CompletableFuture<HttpResponse<byte[]>> futuro : lotesConcurrentes) {
                    verificar(futuro.join().statusCode() == 200 && secuencias(futuro.join().body()).equals(List.of(1L)),
                            "tres clientes reciben su propia secuencia sin bloquearse");
                }
            }
            System.out.println("[OK] Integracion HTTP, lotes binarios, estado y concurrencia superadas.");
        } finally { Files.deleteIfExists(temporal); }
    }
    private static List<Long> secuencias(byte[] lote) throws Exception {
        var salida = new ArrayList<Long>(); var codificador = new CodificadorTramas();
        try (DataInputStream entrada = new DataInputStream(new ByteArrayInputStream(lote))) {
            int cantidad = entrada.readInt();
            for (int i = 0; i < cantidad; i++) salida.add(codificador.decodificar(entrada.readNBytes(entrada.readInt())).secuencia());
        } return salida;
    }
    private static String get(HttpClient c, String url) throws Exception {
        return c.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    }
    private static String postTexto(HttpClient c, String url, String cuerpo) throws Exception {
        return c.send(HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(), HttpResponse.BodyHandlers.ofString()).body();
    }
    private static HttpResponse<byte[]> postRespuesta(HttpClient c, String url, String cuerpo) throws Exception {
        return c.send(HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static void verificar(boolean condicion, String mensaje) { if (!condicion) throw new AssertionError(mensaje); }
}
