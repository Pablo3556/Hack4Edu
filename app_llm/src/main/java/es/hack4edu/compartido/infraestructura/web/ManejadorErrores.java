package es.hack4edu.compartido.infraestructura.web;

import es.hack4edu.compartido.dominio.ErrorAplicacion;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

/** Traduce los errores de aplicación a JSON {"error": "...", …detalle} con el código HTTP que toca. */
@Provider
public class ManejadorErrores implements ExceptionMapper<ErrorAplicacion> {

    @Override
    public Response toResponse(ErrorAplicacion e) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", e.getMessage());
        cuerpo.putAll(e.detalle());
        return Response.status(estado(e.tipo())).type(MediaType.APPLICATION_JSON).entity(cuerpo).build();
    }

    static int estado(ErrorAplicacion.Tipo tipo) {
        return switch (tipo) {
            case PETICION_INVALIDA -> 400;
            case NO_ENCONTRADO -> 404;
            case CONFLICTO -> 409;
            case DEMASIADO_GRANDE -> 413;
            case NO_PROCESABLE -> 422;
            case NO_DISPONIBLE -> 503;
        };
    }
}
