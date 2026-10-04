package blackwall.pruebas;

import blackwall.imagen.CatalogoImagenes;
import blackwall.imagen.DescriptorImagen;
import blackwall.imagen.LectorImagenRegional;
import blackwall.servidor.ServicioProtocoloImagenes;
import blackwall.servidor.ServidorHttpConcurrente;
import blackwall.sesion.GestorSesiones;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

/** Prueba opcional: rutas 24 GB PSB, 55 GB PNG y 93 GB PNG, en ese orden. */
public final class PruebasTresImagenesReales {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Indique las rutas PSB-24, PNG-55 y PNG-93");
        String[] ids = { "imagen24", "imagen55", "imagen93" };
        LectorImagenRegional lector = new LectorImagenRegional(1024L * 1024L);
        CatalogoImagenes catalogo = new CatalogoImagenes(lector);
        for (int i = 0; i < args.length; i++) {
            DescriptorImagen imagen = catalogo.registrar(ids[i], Path.of(args[i]), 256);
            String formatoEsperado = i == 0 ? "PSB" : "PNG";
            if (!imagen.formato().equals(formatoEsperado)) throw new AssertionError(ids[i] + " no es " + formatoEsperado);
            System.out.printf("[OK] %s registrada: %dx%d %s nivel %d.%n",
                    imagen.id(), imagen.ancho(), imagen.alto(), imagen.formato(), imagen.nivelMaximo());
        }
        var servicio = new ServicioProtocoloImagenes(new GestorSesiones(16), catalogo, lector);
        try (var servidor = new ServidorHttpConcurrente(new InetSocketAddress("127.0.0.1", 0), servicio)) {
            servidor.iniciar();
            String base = "http://127.0.0.1:" + servidor.puerto();
            HttpClient cliente = HttpClient.newHttpClient();
            String listado = cliente.send(HttpRequest.newBuilder(URI.create(base + "/protocolo/imagenes")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            for (String id : ids) if (!listado.contains(id + "|")) throw new AssertionError(id + " ausente del catalogo HTTP");
            String sesion = cliente.send(HttpRequest.newBuilder(URI.create(base + "/protocolo/sesiones"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).body().substring(7);
            for (int i = 0; i < ids.length; i++) {
                DescriptorImagen imagen = catalogo.obtener(ids[i]);
                long x = imagen.ancho() / 2, y = imagen.alto() / 2;
                String vista = "sesion=" + sesion + "&imagen=" + ids[i] + "&vista=" + (i + 1)
                        + "&x=" + x + "&y=" + y + "&ancho=256&alto=256&nivel=" + imagen.nivelMaximo() + "&ventana=4";
                String aceptacion = cliente.send(HttpRequest.newBuilder(URI.create(base + "/protocolo/vistas"))
                        .POST(HttpRequest.BodyPublishers.ofString(vista)).build(), HttpResponse.BodyHandlers.ofString()).body();
                if (!aceptacion.contains("ACEPTADA")) throw new AssertionError(ids[i] + " no aceptada: " + aceptacion);
                byte[] lote = cliente.send(HttpRequest.newBuilder(URI.create(base + "/protocolo/lotes"))
                        .POST(HttpRequest.BodyPublishers.ofString("sesion=" + sesion + "&vista=" + (i + 1)))
                        .build(), HttpResponse.BodyHandlers.ofByteArray()).body();
                if (lote.length <= 4) throw new AssertionError(ids[i] + " no entrego un mosaico");
                System.out.printf("[OK] %s seleccionada y entrego un lote de %d bytes.%n", ids[i], lote.length);
            }
        }
    }
}
