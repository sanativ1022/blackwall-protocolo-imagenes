package blackwall.pruebas;

import blackwall.imagen.*;
import blackwall.protocolo.PrioridadDato;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PruebasImagenRegional {
    public static void main(String[] args) throws Exception {
        var temporal = Files.createTempFile("blackwall-region-", ".png");
        try {
            BufferedImage origen = new BufferedImage(2048, 1024, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < origen.getHeight(); y++) for (int x = 0; x < origen.getWidth(); x++)
                origen.setRGB(x, y, ((x & 255) << 16) | ((y & 255) << 8));
            ImageIO.write(origen, "png", temporal.toFile());

            LectorImagenRegional lector = new LectorImagenRegional(512L * 512L);
            DescriptorImagen descriptor = lector.inspeccionar("prueba", temporal, 256);
            verificar(descriptor.ancho() == 2048 && descriptor.alto() == 1024, "inspeccion sin cargar toda la imagen");
            BufferedImage region = lector.leer(descriptor, new RegionImagen(512, 256, 256, 256), descriptor.nivelMaximo());
            verificar(region.getWidth() == 256 && region.getHeight() == 256, "lectura regional exacta");
            boolean rechazada = false;
            try { lector.leer(descriptor, new RegionImagen(0, 0, 1024, 1024), descriptor.nivelMaximo()); }
            catch (IllegalArgumentException esperada) { rechazada = true; }
            verificar(rechazada, "limite de memoria obligatorio");

            SolicitudVista vista = new SolicitudVista(1, new RegionImagen(0, 0, 300, 300), descriptor.nivelMaximo(), 1, 0, 12,
                    1024, 0, 1);
            List<MosaicoSolicitado> plan = new PlanificadorRegiones().planificar(descriptor, vista, true);
            verificar(plan.stream().filter(m -> m.prioridad() == PrioridadDato.VISIBLE_PREVIA).count() == 4, "solo mosaicos visibles");
            verificar(plan.stream().anyMatch(m -> m.prioridad() == PrioridadDato.PREDICCION_PREVIA), "prediccion acotada");
            verificar(plan.stream().filter(m -> m.prioridad() == PrioridadDato.PREDICCION_PREVIA).count() <= 4,
                    "prediccion se limita a una franja pequena");

            CodificadorMosaicos codificador = new CodificadorMosaicos();
            byte[] previa = codificador.previa(region), finalLossless = codificador.finalSinPerdida(region);
            verificar(previa.length > 0 && finalLossless.length > 0, "representaciones progresivas reales");
            verificar(ImageIO.read(new java.io.ByteArrayInputStream(finalLossless)).getRGB(10, 10) == region.getRGB(10, 10),
                    "nivel final conserva pixeles sin perdida");
            System.out.println("[OK] Lectura regional, memoria acotada, planificacion y salida sin perdida superadas.");
            if (args.length > 0) {
                Path real = Path.of(args[0]);
                DescriptorImagen grande = lector.inspeccionar("real", real, 256);
                verificar(grande.formato().equals("PSB") && grande.ancho() == 108199 && grande.alto() == 81503,
                        "metadatos de la imagen ESO");
                BufferedImage centro = lector.leer(grande, new RegionImagen(54000, 40500, 256, 256), grande.nivelMaximo());
                BufferedImage borde = lector.leer(grande, new RegionImagen(108000, 81300, 199, 203), grande.nivelMaximo());
                verificar(centro.getWidth() == 256 && centro.getHeight() == 256
                        && borde.getWidth() == 199 && borde.getHeight() == 203, "lectura de centro y borde PSB");
                verificar(centro.getRGB(0, 0) != centro.getRGB(128, 128), "pixeles PSB no uniformes");
                System.out.printf("[OK] PSB real: %d x %d, centro=%08X, borde=%08X.%n",
                        grande.ancho(), grande.alto(), centro.getRGB(128, 128), borde.getRGB(198, 202));
            }
        } finally { Files.deleteIfExists(temporal); }
    }
    private static void verificar(boolean condicion, String mensaje) { if (!condicion) throw new AssertionError(mensaje); }
}
