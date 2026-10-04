package blackwall.servidor;

import blackwall.imagen.*;
import blackwall.sesion.GestorSesiones;
import java.net.InetSocketAddress;
import java.nio.file.Path;

public final class IniciarServidor {
    public static void main(String[] argumentos) throws Exception {
        if (argumentos.length < 1) {
            System.err.println("Uso: IniciarServidor <imagen> [puerto] | --puerto N --imagen ID=RUTA [--imagen ID=RUTA ...]"); System.exit(2);
        }
        int puerto = 8080;
        LectorImagenRegional lector = new LectorImagenRegional(1024L * 1024L);
        CatalogoImagenes catalogo = new CatalogoImagenes(lector);
        if (argumentos[0].startsWith("--")) {
            for (int i = 0; i < argumentos.length; i++) {
                switch (argumentos[i]) {
                    case "--puerto" -> {
                        if (++i == argumentos.length) throw new IllegalArgumentException("Falta el valor de --puerto");
                        puerto = Integer.parseInt(argumentos[i]);
                    }
                    case "--imagen" -> {
                        if (++i == argumentos.length) throw new IllegalArgumentException("Falta el valor de --imagen");
                        String registro = argumentos[i];
                        int separador = registro.indexOf('=');
                        if (separador < 1 || separador == registro.length() - 1)
                            throw new IllegalArgumentException("Use --imagen ID=RUTA");
                        catalogo.registrar(registro.substring(0, separador), Path.of(registro.substring(separador + 1)), 256);
                    }
                    default -> throw new IllegalArgumentException("Argumento desconocido: " + argumentos[i]);
                }
            }
            if (catalogo.todas().isEmpty()) throw new IllegalArgumentException("Indique al menos una imagen");
        } else {
            if (argumentos.length > 2) throw new IllegalArgumentException("Demasiados argumentos");
            if (argumentos.length == 2) puerto = Integer.parseInt(argumentos[1]);
            catalogo.registrar("principal", Path.of(argumentos[0]), 256);
        }
        ServicioProtocoloImagenes servicio = new ServicioProtocoloImagenes(new GestorSesiones(64), catalogo, lector);
        ServidorHttpConcurrente servidor = new ServidorHttpConcurrente(new InetSocketAddress("127.0.0.1", puerto), servicio, Path.of("interfaz"));
        Runtime.getRuntime().addShutdownHook(new Thread(servidor::close)); servidor.iniciar();
        System.out.printf("Servidor disponible en http://127.0.0.1:%d; %d imagen(es):%n", puerto, catalogo.todas().size());
        for (DescriptorImagen imagen : catalogo.todas())
            System.out.printf("  %s: %dx%d, formato %s.%n", imagen.id(), imagen.ancho(), imagen.alto(), imagen.formato());
    }
}
