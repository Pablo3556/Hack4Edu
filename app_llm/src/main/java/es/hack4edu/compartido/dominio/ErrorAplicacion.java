package es.hack4edu.compartido.dominio;

import java.util.Map;

/**
 * Error de negocio que cualquier capa interna puede lanzar sin conocer HTTP.
 * El adaptador web lo traduce a una respuesta (ver ManejadorErrores).
 */
public class ErrorAplicacion extends RuntimeException {

    public enum Tipo {
        PETICION_INVALIDA, NO_ENCONTRADO, CONFLICTO, DEMASIADO_GRANDE, NO_PROCESABLE, NO_DISPONIBLE
    }

    private final Tipo tipo;
    private final Map<String, Object> detalle;

    private ErrorAplicacion(Tipo tipo, String mensaje, Map<String, Object> detalle) {
        super(mensaje);
        this.tipo = tipo;
        this.detalle = detalle;
    }

    public Tipo tipo() {
        return tipo;
    }

    /** Datos adicionales que se devuelven junto al mensaje (por ejemplo, la lista de bloqueos). */
    public Map<String, Object> detalle() {
        return detalle;
    }

    public static ErrorAplicacion peticionInvalida(String mensaje) {
        return new ErrorAplicacion(Tipo.PETICION_INVALIDA, mensaje, Map.of());
    }

    public static ErrorAplicacion noEncontrado(String mensaje) {
        return new ErrorAplicacion(Tipo.NO_ENCONTRADO, mensaje, Map.of());
    }

    public static ErrorAplicacion conflicto(String mensaje, Map<String, Object> detalle) {
        return new ErrorAplicacion(Tipo.CONFLICTO, mensaje, detalle);
    }

    public static ErrorAplicacion demasiadoGrande(String mensaje) {
        return new ErrorAplicacion(Tipo.DEMASIADO_GRANDE, mensaje, Map.of());
    }

    public static ErrorAplicacion noProcesable(String mensaje) {
        return new ErrorAplicacion(Tipo.NO_PROCESABLE, mensaje, Map.of());
    }

    public static ErrorAplicacion noDisponible(String mensaje) {
        return new ErrorAplicacion(Tipo.NO_DISPONIBLE, mensaje, Map.of());
    }
}
