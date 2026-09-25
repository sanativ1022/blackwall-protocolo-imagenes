package blackwall.protocolo;

import java.util.List;

public record ConfirmacionRecepcion(long idVista, long ackAcumulativo, List<RangoSack> rangosSack,
                                    int ventanaReceptoraPaquetes, long marcaEcoNanos) {
    public ConfirmacionRecepcion {
        if (idVista <= 0 || ackAcumulativo < 0 || ventanaReceptoraPaquetes < 0) {
            throw new IllegalArgumentException("Confirmacion invalida");
        }
        rangosSack = List.copyOf(rangosSack);
    }

    public boolean confirma(long secuencia) {
        return secuencia <= ackAcumulativo || rangosSack.stream().anyMatch(rango -> rango.contiene(secuencia));
    }
}
