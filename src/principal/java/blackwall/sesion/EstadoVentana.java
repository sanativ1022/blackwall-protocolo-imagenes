package blackwall.sesion;

public record EstadoVentana(int pendientes, int enVuelo, long bytesEnVuelo, long confirmados,
                            long retransmisiones, double ventanaCongestion, int ventanaReceptora,
                            long rtoMs, double rttMs, String faseCongestion) {}
