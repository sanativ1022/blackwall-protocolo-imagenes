package blackwall.imagen;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LectorImagenRegional {
    private final long maximoPixeles;
    private final Map<Path, LectorPngGigante> pngGigantes = new ConcurrentHashMap<>();
    public LectorImagenRegional(long maximoPixeles) {
        if (maximoPixeles < 4096) throw new IllegalArgumentException("Limite demasiado pequeno");
        this.maximoPixeles = maximoPixeles;
    }

    public DescriptorImagen inspeccionar(String id, Path archivo, int tamanoMosaico) throws IOException {
        if (archivo.getFileName().toString().toLowerCase().endsWith(".psb")) {
            var psb = LectorPsbRegional.inspeccionar(archivo);
            int maximo = 0; long mayor = Math.max(psb.ancho(), psb.alto());
            while ((1L << maximo) * tamanoMosaico < mayor) maximo++;
            return new DescriptorImagen(id, archivo.toAbsolutePath().normalize(), psb.ancho(), psb.alto(),
                    "PSB", tamanoMosaico, maximo);
        }
        try (ImageInputStream entrada = ImageIO.createImageInputStream(Files.newInputStream(archivo))) {
            ImageReader lector = lectorPara(entrada);
            try {
                lector.setInput(entrada, true, true);
                int ancho = lector.getWidth(0), alto = lector.getHeight(0);
                int maximo = 0; long mayor = Math.max(ancho, alto);
                while ((1L << maximo) * tamanoMosaico < mayor) maximo++;
                String formato = lector.getFormatName().toUpperCase();
                return new DescriptorImagen(id, archivo.toAbsolutePath().normalize(), ancho, alto, formato, tamanoMosaico, maximo);
            } finally { lector.dispose(); }
        }
    }

    public BufferedImage leer(DescriptorImagen descriptor, RegionImagen region, int nivel) throws IOException {
        if (nivel < 0 || nivel > descriptor.nivelMaximo()) throw new IllegalArgumentException("Nivel invalido");
        if (region.derecha() > descriptor.ancho() || region.abajo() > descriptor.alto()) throw new IllegalArgumentException("Region fuera de imagen");
        int submuestreo = 1 << Math.min(30, descriptor.nivelMaximo() - nivel);
        long pixelesSalida = ((region.ancho() + submuestreo - 1L) / submuestreo)
                * ((region.alto() + submuestreo - 1L) / submuestreo);
        if (pixelesSalida > maximoPixeles) throw new IllegalArgumentException("La region supera el limite de memoria");
        if (descriptor.formato().equals("PSB")) {
            var psb = LectorPsbRegional.inspeccionar(descriptor.archivo());
            if (psb.ancho() != descriptor.ancho() || psb.alto() != descriptor.alto())
                throw new IOException("El archivo PSB cambio desde su registro");
            return LectorPsbRegional.leer(descriptor.archivo(), psb, region, submuestreo);
        }
        if (descriptor.formato().equals("PNG") && (long) descriptor.ancho() * descriptor.alto() > Integer.MAX_VALUE) {
            LectorPngGigante gigante = pngGigantes.get(descriptor.archivo());
            if (gigante == null) {
                synchronized (pngGigantes) {
                    gigante = pngGigantes.get(descriptor.archivo());
                    if (gigante == null) {
                        gigante = new LectorPngGigante(descriptor.archivo(), Math.toIntExact(descriptor.ancho()),
                                Math.toIntExact(descriptor.alto()));
                        pngGigantes.put(descriptor.archivo(), gigante);
                    }
                }
            }
            return gigante.leer(region, submuestreo);
        }
        try (ImageInputStream entrada = ImageIO.createImageInputStream(Files.newInputStream(descriptor.archivo()))) {
            ImageReader lector = lectorPara(entrada);
            try {
                lector.setInput(entrada, true, true);
                var parametros = lector.getDefaultReadParam();
                parametros.setSourceRegion(new Rectangle(Math.toIntExact(region.x()), Math.toIntExact(region.y()), region.ancho(), region.alto()));
                parametros.setSourceSubsampling(submuestreo, submuestreo, 0, 0);
                return lector.read(0, parametros);
            } finally { lector.dispose(); }
        }
    }

    private ImageReader lectorPara(ImageInputStream entrada) throws IOException {
        Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
        if (!lectores.hasNext()) throw new IOException("Formato no soportado por ImageIO");
        return lectores.next();
    }
}
