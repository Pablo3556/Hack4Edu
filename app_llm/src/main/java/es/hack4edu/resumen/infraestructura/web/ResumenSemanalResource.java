package es.hack4edu.resumen.infraestructura.web;

import es.hack4edu.compartido.dominio.Ambito;
import es.hack4edu.resumen.aplicacion.GenerarResumenSemanal;
import es.hack4edu.resumen.dominio.ResumenSemanal;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/** Adaptador de entrada REST del resumen semanal. */
@Path("/api/resumen-semanal")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
@RolesAllowed("docente")
public class ResumenSemanalResource {

    @Inject
    GenerarResumenSemanal resumenSemanal;

    @GET
    public ResumenSemanal.Resumen resumen(@QueryParam("centro") Integer centro, @QueryParam("grupo") Integer grupo) {
        return resumenSemanal.resumen(Ambito.de(centro, grupo));
    }
}
