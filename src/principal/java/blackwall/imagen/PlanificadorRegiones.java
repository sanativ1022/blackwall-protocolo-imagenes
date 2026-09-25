package blackwall.imagen;

import blackwall.protocolo.PrioridadDato;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PlanificadorRegiones {
    public List<MosaicoSolicitado> planificar(DescriptorImagen imagen, SolicitudVista vista, boolean permitirPrediccion) {
        return planificar(imagen, vista, permitirPrediccion, 250);
    }

    public List<MosaicoSolicitado> planificar(DescriptorImagen imagen, SolicitudVista vista, boolean permitirPrediccion, double rttMs) {
        if (vista.nivel() > imagen.nivelMaximo()) throw new IllegalArgumentException("Nivel inexistente");
        long escala = 1L << Math.min(30, imagen.nivelMaximo() - vista.nivel());
        long lado = (long) imagen.tamanoMosaico() * escala;
        RegionImagen visible = recortar(vista.regionVisible(), imagen);
        List<MosaicoSolicitado> resultado = mosaicos(imagen, visible, vista.nivel(), false);
        if (permitirPrediccion && vista.estabilidad() >= 0.75
                && (vista.velocidadX() > 0 || vista.velocidadY() > 0)
                && vista.anchoReceptor() > 0 && (vista.direccionX() != 0 || vista.direccionY() != 0)) {
            double horizonte = Math.max(0.15, Math.min(0.8, rttMs / 1000.0 * 2));
            long avanceX = Math.min(lado, Math.round(Math.abs(vista.velocidadX()) * horizonte)) * vista.direccionX();
            long avanceY = Math.min(lado, Math.round(Math.abs(vista.velocidadY()) * horizonte)) * vista.direccionY();
            RegionImagen futura = recortar(new RegionImagen(Math.max(0, visible.x() + avanceX),
                    Math.max(0, visible.y() + avanceY), visible.ancho(), visible.alto()), imagen);
            mosaicos(imagen, futura, vista.nivel(), true).stream()
                    .filter(m -> resultado.stream().noneMatch(actual -> actual.idDato().equals(m.idDato())))
                    .limit(4).forEach(resultado::add);
        }
        return resultado;
    }

    private List<MosaicoSolicitado> mosaicos(DescriptorImagen imagen, RegionImagen region, int nivel, boolean prediccion) {
        long escala = 1L << Math.min(30, imagen.nivelMaximo() - nivel);
        long lado = (long) imagen.tamanoMosaico() * escala;
        long columnaInicio = region.x() / lado, columnaFin = (region.derecha() - 1) / lado;
        long filaInicio = region.y() / lado, filaFin = (region.abajo() - 1) / lado;
        if ((columnaFin - columnaInicio + 1) * (filaFin - filaInicio + 1) > 64)
            throw new IllegalArgumentException("La vista cubre demasiados mosaicos para este nivel");
        List<MosaicoSolicitado> salida = new ArrayList<>();
        for (long fila = filaInicio; fila <= filaFin; fila++) for (long columna = columnaInicio; columna <= columnaFin; columna++) {
            long x = columna * lado, y = fila * lado;
            int ancho = (int) Math.min(lado, imagen.ancho() - x), alto = (int) Math.min(lado, imagen.alto() - y);
            RegionImagen mosaico = new RegionImagen(x, y, ancho, alto);
            String base = imagen.id() + ":" + nivel + ":" + columna + ":" + fila;
            salida.add(new MosaicoSolicitado(base + ":P", mosaico, nivel,
                    prediccion ? PrioridadDato.PREDICCION_PREVIA : PrioridadDato.VISIBLE_PREVIA));
            if (!prediccion) salida.add(new MosaicoSolicitado(base + ":F", mosaico, nivel, PrioridadDato.VISIBLE_FINAL));
        }
        salida.sort(Comparator.comparingInt((MosaicoSolicitado m) -> m.prioridad().orden())
                .thenComparingLong(m -> -interseccion(m.region(), region)));
        return salida;
    }

    private long interseccion(RegionImagen a, RegionImagen b) {
        long ancho = Math.max(0, Math.min(a.derecha(), b.derecha()) - Math.max(a.x(), b.x()));
        long alto = Math.max(0, Math.min(a.abajo(), b.abajo()) - Math.max(a.y(), b.y()));
        return ancho * alto;
    }

    private RegionImagen recortar(RegionImagen region, DescriptorImagen imagen) {
        long x = Math.min(region.x(), imagen.ancho() - 1), y = Math.min(region.y(), imagen.alto() - 1);
        int ancho = (int) Math.min(region.ancho(), imagen.ancho() - x);
        int alto = (int) Math.min(region.alto(), imagen.alto() - y);
        return new RegionImagen(x, y, ancho, alto);
    }
}
