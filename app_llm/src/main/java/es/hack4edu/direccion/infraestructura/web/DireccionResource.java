package es.hack4edu.direccion.infraestructura.web;

import es.hack4edu.compartido.infraestructura.web.ControlAcceso;
import es.hack4edu.direccion.aplicacion.ConsultarProgresoDocente;
import es.hack4edu.direccion.aplicacion.ConsultarSemaforo;
import es.hack4edu.direccion.dominio.PanelDireccion;
import es.hack4edu.direccion.dominio.ProgresoDocente;
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

    @Inject
    ConsultarProgresoDocente progreso;

    @Inject
    ControlAcceso acceso;

    @GET
    @Path("/semaforo")
    public PanelDireccion.Panel semaforo(@QueryParam("centro") Integer centro) {
        acceso.comprobar(centro, null);
        return semaforo.semaforo(centro);
    }

    /** Progreso del alumnado frente a lo esperado, por grupo y por docente del centro. */
    @GET
    @Path("/progreso-docente")
    public ProgresoDocente.Panel progresoDocente(@QueryParam("centro") Integer centro) {
        acceso.comprobar(centro, null);
        return progreso.progreso(centro);
    }
}
