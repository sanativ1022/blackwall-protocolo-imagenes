package blackwall.servidor;

import blackwall.imagen.*;
import blackwall.protocolo.*;
import blackwall.sesion.*;
import java.io.*;
import java.util.*;

public final class ServicioProtocoloImagenes {
    private final GestorSesiones sesiones;
    private final CatalogoImagenes catalogo;
    private final LectorImagenRegional lector;
    private final PlanificadorRegiones planificador = new PlanificadorRegiones();
    private final CodificadorMosaicos codificador = new CodificadorMosaicos();
    private final CodificadorTramas tramas = new CodificadorTramas();

    public ServicioProtocoloImagenes(GestorSesiones sesiones, CatalogoImagenes catalogo, LectorImagenRegional lector) {
        this.sesiones = sesiones; this.catalogo = catalogo; this.lector = lector;
    }
    public String crearSesion() { return sesiones.crear().id(); }

    public int iniciarVista(String idSesion, String idImagen, SolicitudVista vista, Set<String> cache) throws IOException {
        SesionTransferencia sesion = sesiones.obtener(idSesion);
        DescriptorImagen imagen = catalogo.obtener(idImagen);
        List<MosaicoSolicitado> mosaicos = planificador.planificar(imagen, vista, true, sesion.rttMs());
        if (mosaicos.size() > 128) throw new IllegalArgumentException("La vista solicita demasiados mosaicos; reduzca la region o el nivel");
        List<TrabajoMosaico> trabajos = new ArrayList<>();
        for (MosaicoSolicitado mosaico : mosaicos) {
            trabajos.add(new TrabajoMosaico(mosaico.idDato(), mosaico.prioridad(), () -> {
                var pixeles = lector.leer(imagen, mosaico.region(), mosaico.nivel());
                return switch (mosaico.prioridad()) {
                    case VISIBLE_PREVIA, PREDICCION_PREVIA -> codificador.previa(pixeles);
                    case VISIBLE_FINAL, PREDICCION_FINAL -> codificador.finalSinPerdida(pixeles);
                };
            }));
        }
        sesion.iniciarVista(vista.idVista(), cache);
        return sesion.programar(trabajos);
    }

    public byte[] confirmarYEmitir(String idSesion, ConfirmacionRecepcion confirmacion) throws IOException {
        SesionTransferencia sesion = sesiones.obtener(idSesion);
        long ahora = System.nanoTime();
        if (confirmacion != null && confirmacion.idVista() != sesion.vistaActual()) {
            return new byte[]{0, 0, 0, 0};
        }
        if (confirmacion != null) sesion.confirmar(confirmacion, ahora);
        List<PaqueteDatos> lote = sesion.emitirPreparando(ahora);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream salida = new DataOutputStream(bytes)) {
            salida.writeInt(lote.size());
            for (PaqueteDatos paquete : lote) {
                byte[] trama = tramas.codificar(paquete); salida.writeInt(trama.length); salida.write(trama);
            }
            return bytes.toByteArray();
        }
    }
    public EstadoVentana estado(String idSesion) { return sesiones.obtener(idSesion).estado(); }
    public long vistaActual(String idSesion) { return sesiones.obtener(idSesion).vistaActual(); }
    public int prediccionesPendientes(String idSesion) { return sesiones.obtener(idSesion).prediccionesPendientes(); }
    public SesionTransferencia sesion(String idSesion) { return sesiones.obtener(idSesion); }
    public Collection<DescriptorImagen> imagenes() { return catalogo.todas(); }
}
