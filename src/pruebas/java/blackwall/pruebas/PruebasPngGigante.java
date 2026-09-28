package blackwall.pruebas;

import blackwall.imagen.LectorImagenRegional;
import blackwall.imagen.RegionImagen;
import java.nio.file.Path;

/** Prueba opcional contra el archivo oficial; no forma parte de la batería sin datos. */
public final class PruebasPngGigante {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Indique la ruta del PNG oficial");
        LectorImagenRegional lector = new LectorImagenRegional(1024L * 1024);
        var imagen = lector.inspeccionar("oficial55", Path.of(args[0]), 256);
        if (!imagen.formato().equals("PNG") || imagen.ancho() != 136325 || imagen.alto() != 136325
                || imagen.nivelMaximo() != 10) throw new AssertionError("Metadatos inesperados del PNG oficial");
        var esquina = lector.leer(imagen, new RegionImagen(0, 0, 256, 256), 10);
        var centro = lector.leer(imagen, new RegionImagen(68000, 68000, 256, 256), 10);
        var borde = lector.leer(imagen, new RegionImagen(136069, 136069, 256, 256), 10);
        var general = lector.leer(imagen, new RegionImagen(0, 0, 136325, 136325), 0);
        if (esquina.getWidth() != 256 || centro.getHeight() != 256 || borde.getHeight() != 256
                || general.getWidth() != 134)
            throw new AssertionError("Dimensiones regionales incorrectas");
        System.out.printf("[OK] PNG 55 GB: esquina=%08X, centro=%08X, borde=%08X, vista general=%dx%d.%n",
                esquina.getRGB(0, 0), centro.getRGB(128, 128), borde.getRGB(255, 255),
                general.getWidth(), general.getHeight());
    }
}
