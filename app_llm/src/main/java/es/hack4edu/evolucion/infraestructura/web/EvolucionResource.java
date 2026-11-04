package es.hack4edu.evolucion.infraestructura.web;

import es.hack4edu.compartido.dominio.Ambito;
import es.hack4edu.evolucion.aplicacion.ConsultarEvolucion;
import es.hack4edu.evolucion.dominio.InformeEvolucion;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/** Adaptador de entrada REST: evolución agregada de un grupo o centro, con alertas tempranas. */
@Path("/api/evolucion")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
@RolesAllowed("docente")
public class EvolucionResource {

    @Inject
    ConsultarEvolucion evolucion;

    @GET
    public InformeEvolucion.Evolucion evolucion(@QueryParam("centro") Integer centro, @QueryParam("grupo") Integer grupo) {
        return evolucion.evolucion(Ambito.de(centro, grupo));
    }
}
