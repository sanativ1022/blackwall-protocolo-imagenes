package blackwall.imagen;

import blackwall.protocolo.PrioridadDato;

public record MosaicoSolicitado(String idDato, RegionImagen region, int nivel, PrioridadDato prioridad) {}
