package es.hack4edu.direccion.infraestructura.web;

import es.hack4edu.direccion.aplicacion.ConsultarSemaforo;
import es.hack4edu.direccion.dominio.PanelDireccion;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/** Adaptador de entrada REST del panel de dirección. */
@Path("/api/direccion")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
@RolesAllowed({"direccion", "administracion"})
public class DireccionResource {

    @Inject
    ConsultarSemaforo semaforo;

    @GET
    @Path("/semaforo")
    public PanelDireccion.Panel semaforo(@QueryParam("centro") Integer centro) {
        return semaforo.semaforo(centro);
    }
}
