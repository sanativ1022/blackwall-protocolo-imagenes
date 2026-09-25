package blackwall.pruebas;

import blackwall.imagen.*;
import blackwall.protocolo.*;
import blackwall.sesion.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class PruebasAdaptacionFlare {
    public static void main(String[] args) throws Exception {
        DescriptorImagen imagen = new DescriptorImagen("imagen", Path.of("imagen.png"), 2048, 1024, "PNG", 256, 3);
        PlanificadorRegiones planificador = new PlanificadorRegiones();
        SolicitudVista inestable = new SolicitudVista(1, new RegionImagen(200, 200, 300, 300), 3, 1, 0, 16,
                1024, 0, 0.4);
        List<MosaicoSolicitado> soloVisible = planificador.planificar(imagen, inestable, true);
        verificar(soloVisible.stream().noneMatch(m -> m.prioridad() == PrioridadDato.PREDICCION_PREVIA),
                "movimiento inestable no genera prediccion");
        verificar(soloVisible.getFirst().idDato().contains(":1:1:P"),
                "el mosaico con mayor superficie visible llega primero");
        SolicitudVista estable = new SolicitudVista(2, inestable.regionVisible(), 3, 1, 0, 16,
                1024, 0, 1);
        List<MosaicoSolicitado> conPrediccion = planificador.planificar(imagen, estable, true);
        verificar(conPrediccion.stream().anyMatch(m -> m.prioridad() == PrioridadDato.PREDICCION_PREVIA),
                "prediccion solo con movimiento estable");
        verificar(conPrediccion.stream().filter(m -> m.prioridad() == PrioridadDato.PREDICCION_PREVIA).count() <= 4,
                "franja predicha acotada");

        SesionTransferencia sesion = new SesionTransferencia("0123456789abcdef", 16);
        AtomicInteger lecturas = new AtomicInteger();
        sesion.iniciarVista(1, Set.of());
        sesion.programar(List.of(trabajo("a:P", PrioridadDato.VISIBLE_PREVIA, lecturas),
                trabajo("b:P", PrioridadDato.VISIBLE_PREVIA, lecturas),
                trabajo("c:F", PrioridadDato.VISIBLE_FINAL, lecturas)));
        verificar(lecturas.get() == 0, "crear vista no lee ni codifica imagen");
        List<PaqueteDatos> primero = sesion.emitirPreparando(1_000_000_000L);
        verificar(primero.size() == 1 && lecturas.get() == 1 && primero.getFirst().idDato().equals("a:P"),
                "la primera respuesta termina con una sola previa visible");
        List<PaqueteDatos> segundo = sesion.emitirPreparando(1_010_000_000L);
        verificar(segundo.size() == 1 && lecturas.get() == 2 && segundo.getFirst().idDato().equals("b:P"),
                "la siguiente previa no queda retenida por un mosaico final grande");
        sesion.confirmar(new ConfirmacionRecepcion(1, 2, List.of(), 16, 0), 1_020_000_000L);
        verificar(sesion.estado().ventanaCongestion() >= 4, "ACK abre ventana");
        sesion.iniciarVista(2, Set.of());
        verificar(sesion.estado().ventanaCongestion() >= 4 && sesion.rttMs() > 0,
                "RTT y congestion permanecen entre vistas");
        sesion.programar(List.of(new TrabajoMosaico("a:P", PrioridadDato.VISIBLE_PREVIA,
                () -> { throw new AssertionError("mosaico conservado fue releido"); })));
        verificar(sesion.emitirPreparando(2_000_000_000L).getFirst().secuencia() == 1,
                "la secuencia se reinicia solo para la vista nueva y reutiliza mosaico preparado");

        SesionTransferencia cache = new SesionTransferencia("fedcba9876543210", 16);
        cache.iniciarVista(1, Set.of("imagen:3:0:0:F"));
        verificar(cache.programar(List.of(trabajo("imagen:3:0:0:P", PrioridadDato.VISIBLE_PREVIA, lecturas),
                trabajo("imagen:3:0:0:F", PrioridadDato.VISIBLE_FINAL, lecturas))) == 0,
                "final en cache elimina ambas calidades de la solicitud");

        lecturas.set(0);
        SesionTransferencia prediccion = new SesionTransferencia("1111111111111111", 16);
        prediccion.iniciarVista(1, Set.of());
        prediccion.programar(List.of(trabajo("v1:P", PrioridadDato.VISIBLE_PREVIA, lecturas),
                trabajo("v2:P", PrioridadDato.VISIBLE_PREVIA, lecturas),
                trabajo("p:P", PrioridadDato.PREDICCION_PREVIA, lecturas)));
        prediccion.emitirPreparando(3_000_000_000L);
        prediccion.emitirPreparando(3_010_000_000L);
        verificar(prediccion.prediccionesPendientes() == 1 && lecturas.get() == 2,
                "prediccion no se prepara mientras falta la vista visible");
        prediccion.confirmar(new ConfirmacionRecepcion(1, 2, List.of(), 16, 0), 3_020_000_000L);
        verificar(prediccion.emitirPreparando(3_030_000_000L).getFirst().idDato().equals("p:P"),
                "prediccion usa capacidad libre tras confirmar los visibles");

        SesionTransferencia lenta = new SesionTransferencia("2222222222222222", 16);
        lenta.iniciarVista(1, Set.of());
        lenta.programar(List.of(trabajo("v1:P", PrioridadDato.VISIBLE_PREVIA, lecturas),
                trabajo("v2:P", PrioridadDato.VISIBLE_PREVIA, lecturas),
                trabajo("v3:F", PrioridadDato.VISIBLE_FINAL, lecturas),
                trabajo("p:P", PrioridadDato.PREDICCION_PREVIA, lecturas)));
        lenta.emitirPreparando(4_000_000_000L);
        lenta.emitirPreparando(4_010_000_000L);
        lenta.confirmar(new ConfirmacionRecepcion(1, 2, List.of(), 16, 0), 4_020_000_000L);
        lenta.emitirPreparando(4_030_000_000L);
        lenta.confirmar(new ConfirmacionRecepcion(1, 3, List.of(), 16, 0), 4_230_000_000L);
        verificar(lenta.emitirPreparando(4_240_000_000L).isEmpty() && lenta.prediccionesPendientes() == 0
                && lenta.estado().pendientes() == 0,
                "RTT alto descarta prediccion opcional y permite concluir la vista");
        int antesDeCambiar = lecturas.get();
        lenta.iniciarVista(2, Set.of());
        verificar(lenta.prediccionesPendientes() == 0 && lecturas.get() == antesDeCambiar,
                "cambiar de direccion descarta prediccion sin leerla");
        System.out.println("[OK] Adaptacion Flare: cobertura, movimiento, preparacion diferida, cache, continuidad y prediccion.");
    }

    private static TrabajoMosaico trabajo(String id, PrioridadDato prioridad, AtomicInteger lecturas) {
        return new TrabajoMosaico(id, prioridad, () -> { lecturas.incrementAndGet(); return new byte[]{1, 2, 3}; });
    }
    private static void verificar(boolean condicion, String mensaje) { if (!condicion) throw new AssertionError(mensaje); }
}
