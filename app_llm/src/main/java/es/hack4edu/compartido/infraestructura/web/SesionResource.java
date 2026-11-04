package es.hack4edu.compartido.infraestructura.web;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sesión del usuario en la web. El login lo hace Quarkus (formulario de /login.html → /j_security_check);
 * aquí solo se dice quién es y se cierra la sesión.
 *   GET  /api/sesion        → usuario, rol principal, roles y el centro/grupo que puede consultar
 *   POST /api/sesion/salir  → borra la cookie de sesión
 */
@Path("/api/sesion")
@Produces(MediaType.APPLICATION_JSON)
public class SesionResource {

    /** Cookie que usa la autenticación por formulario de Quarkus (quarkus.http.auth.form.cookie-name). */
    static final String COOKIE = "quarkus-credential";

    /** De más a menos amplio: la web muestra lo que corresponde al primero que tenga el usuario. */
    static final List<String> ROLES = List.of("administracion", "direccion", "docente");

    @Inject
    SecurityIdentity identidad;

    @Inject
    ControlAcceso acceso;

    @GET
    @Authenticated
    public Map<String, Object> actual() {
        List<String> roles = ROLES.stream().filter(identidad::hasRole).toList();
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("usuario", identidad.getPrincipal().getName());
        datos.put("rol", roles.isEmpty() ? "" : roles.get(0));
        datos.put("roles", roles);
        if (!roles.isEmpty()) {
            ControlAcceso.Permiso p = acceso.permiso();
            datos.put("centro", p.centro());   // null = todos los centros
            datos.put("grupo", p.grupo());     // null = todos los grupos del centro
        }
        return datos;
    }

    @POST
    @Path("/salir")
    public Response salir() {
        NewCookie borrar = new NewCookie.Builder(COOKIE).value("").path("/").maxAge(0).httpOnly(true).build();
        return Response.noContent().cookie(borrar).build();
    }
}
