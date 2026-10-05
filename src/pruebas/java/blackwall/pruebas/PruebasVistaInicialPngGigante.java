package blackwall.pruebas;

import blackwall.imagen.LectorImagenRegional;
import blackwall.imagen.RegionImagen;
import java.nio.file.Path;

/** Prueba opcional con el PNG oficial: la primera vista no debe quedar vacia. */
public final class PruebasVistaInicialPngGigante {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Indique la ruta del PNG gigante");
        var lector = new LectorImagenRegional(1024L * 1024L);
        var imagen = lector.inspeccionar("oficial", Path.of(args[0]), 256);
        long inicio = System.nanoTime();
        var general = lector.leer(imagen,
                new RegionImagen(0, 0, Math.toIntExact(imagen.ancho()), Math.toIntExact(imagen.alto())), 0);
        long milisegundos = (System.nanoTime() - inicio) / 1_000_000L;
        int referencia = general.getRGB(0, 0);
        boolean distintos = false;
        for (int y = 0; y < general.getHeight() && !distintos; y++) {
            for (int x = 0; x < general.getWidth(); x++) {
                if (general.getRGB(x, y) != referencia) { distintos = true; break; }
            }
        }
        if (!distintos) throw new AssertionError("La primera vista contiene un solo color");
        System.out.printf("[OK] Vista inicial %dx%d con contenido, preparada en %d ms.%n",
                general.getWidth(), general.getHeight(), milisegundos);
    }
}
