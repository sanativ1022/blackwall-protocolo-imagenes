package blackwall.sesion;

import blackwall.protocolo.PrioridadDato;
import java.io.IOException;

public record TrabajoMosaico(String idDato, PrioridadDato prioridad, ProveedorContenido proveedor) {
    @FunctionalInterface public interface ProveedorContenido { byte[] cargar() throws IOException; }
}
