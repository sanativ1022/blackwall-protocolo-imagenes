package blackwall.imagen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CatalogoImagenes {
    private final Map<String, DescriptorImagen> imagenes = new ConcurrentHashMap<>();
    private final LectorImagenRegional lector;
    public CatalogoImagenes(LectorImagenRegional lector) { this.lector = lector; }

    public DescriptorImagen registrar(String id, Path archivo, int tamanoMosaico) throws IOException {
        if (!Files.isRegularFile(archivo)) throw new IOException("No existe el archivo: " + archivo);
        DescriptorImagen descriptor = lector.inspeccionar(id, archivo, tamanoMosaico);
        if (imagenes.putIfAbsent(id, descriptor) != null) throw new IllegalArgumentException("Id de imagen repetido");
        return descriptor;
    }
    public DescriptorImagen obtener(String id) {
        DescriptorImagen descriptor = imagenes.get(id);
        if (descriptor == null) throw new IllegalArgumentException("Imagen desconocida");
        return descriptor;
    }
    public Collection<DescriptorImagen> todas() {
        return imagenes.values().stream().sorted(Comparator.comparing(DescriptorImagen::id)).toList();
    }
}
