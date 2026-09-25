package blackwall.servidor;

import blackwall.imagen.*;
import blackwall.sesion.GestorSesiones;
import java.net.InetSocketAddress;
import java.nio.file.Path;

public final class IniciarServidor {
    public static void main(String[] argumentos) throws Exception {
        if (argumentos.length < 1) {
            System.err.println("Uso: java ... blackwall.servidor.IniciarServidor <imagen> [puerto]"); System.exit(2);
        }
        int puerto = argumentos.length > 1 ? Integer.parseInt(argumentos[1]) : 8080;
        LectorImagenRegional lector = new LectorImagenRegional(1024L * 1024L);
        CatalogoImagenes catalogo = new CatalogoImagenes(lector);
        DescriptorImagen imagen = catalogo.registrar("principal", Path.of(argumentos[0]), 256);
        ServicioProtocoloImagenes servicio = new ServicioProtocoloImagenes(new GestorSesiones(64), catalogo, lector);
        ServidorHttpConcurrente servidor = new ServidorHttpConcurrente(new InetSocketAddress("127.0.0.1", puerto), servicio, Path.of("interfaz"));
        Runtime.getRuntime().addShutdownHook(new Thread(servidor::close)); servidor.iniciar();
        System.out.printf("Servidor disponible en http://127.0.0.1:%d; imagen %dx%d, formato %s.%n", puerto, imagen.ancho(), imagen.alto(), imagen.formato());
    }
}
