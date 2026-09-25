package blackwall.servidor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ParametrosPeticion {
    private final Map<String, String> valores = new HashMap<>();
    public ParametrosPeticion(String texto) {
        if (texto == null || texto.isBlank()) return;
        for (String par : texto.split("&")) {
            int separador = par.indexOf('=');
            String clave = decodificar(separador < 0 ? par : par.substring(0, separador));
            String valor = decodificar(separador < 0 ? "" : par.substring(separador + 1));
            valores.put(clave, valor);
        }
    }
    public String requerido(String clave) {
        String valor = valores.get(clave);
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException("Falta parametro: " + clave);
        return valor;
    }
    public long largo(String clave, long defecto) { String v = valores.get(clave); return v == null ? defecto : Long.parseLong(v); }
    public int entero(String clave, int defecto) { return Math.toIntExact(largo(clave, defecto)); }
    public double decimal(String clave, double defecto) {
        String valor = valores.get(clave); return valor == null ? defecto : Double.parseDouble(valor);
    }
    public Set<String> conjunto(String clave) {
        String valor = valores.get(clave); return valor == null || valor.isBlank() ? Set.of() : Set.of(valor.split(","));
    }
    public String opcional(String clave, String defecto) { return valores.getOrDefault(clave, defecto); }
    private static String decodificar(String texto) { return URLDecoder.decode(texto, StandardCharsets.UTF_8); }
}
