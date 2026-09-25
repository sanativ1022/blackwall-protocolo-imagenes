package blackwall.imagen;

import java.nio.file.Path;

public record DescriptorImagen(String id, Path archivo, long ancho, long alto, String formato,
                               int tamanoMosaico, int nivelMaximo) {
    public DescriptorImagen {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,80}")) throw new IllegalArgumentException("Id invalido");
        if (archivo == null || ancho <= 0 || alto <= 0 || tamanoMosaico < 64 || nivelMaximo < 0)
            throw new IllegalArgumentException("Descriptor invalido");
    }
}
