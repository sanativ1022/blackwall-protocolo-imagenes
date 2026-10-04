package blackwall.pruebas;

import blackwall.sesion.GestorSesiones;

public final class PruebasLimpiezaSesiones {
    public static void main(String[] args) throws Exception {
        try (var gestor = new GestorSesiones(4, 60, 10)) {
            String id = gestor.crear().id();
            if (gestor.cantidad() != 1) throw new AssertionError("La sesion no se creo");
            long limite = System.nanoTime() + 2_000_000_000L;
            while (gestor.cantidad() != 0 && System.nanoTime() < limite) Thread.sleep(10);
            if (gestor.cantidad() != 0) throw new AssertionError("La limpieza periodica no retiro la sesion inactiva");
            boolean desconocida = false;
            try { gestor.obtener(id); } catch (IllegalArgumentException esperada) { desconocida = true; }
            if (!desconocida) throw new AssertionError("La sesion expirada sigue disponible");
        }
        System.out.println("[OK] Limpieza periodica de sesiones inactivas.");
    }
}
