package es.hack4edu.ingesta.infraestructura.web;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Con HTTP Basic el navegador reenvía las credenciales solo, así que otra web podría
 * intentar enviar un formulario a /api/ingesta en nombre del docente (CSRF). Las
 * peticiones que modifican datos deben llevar una cabecera propia: un formulario de otra
 * web no puede añadirla y, al no haber CORS habilitado, un fetch de otro origen tampoco.
 */
public class FiltroAntiCsrf {

    public static final String CABECERA = "X-AdAstra";
    private static final Set<String> SEGUROS = Set.of("GET", "HEAD", "OPTIONS");

    @ServerRequestFilter
    public Optional<Response> comprobar(ContainerRequestContext ctx) {
        String ruta = ctx.getUriInfo().getPath();
        boolean protegida = ruta.startsWith("/api/ingesta") || ruta.startsWith("/api/medidas");
        if (!protegida || SEGUROS.contains(ctx.getMethod())) {
            return Optional.empty();
        }
        if ("1".equals(ctx.getHeaderString(CABECERA))) {
            return Optional.empty();
        }
        return Optional.of(Response.status(Response.Status.FORBIDDEN).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "Petición rechazada: falta la cabecera " + CABECERA + ".")).build());
    }
}
