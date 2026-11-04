package es.hack4edu.medidas.infraestructura.web;

import es.hack4edu.compartido.infraestructura.web.ControlAcceso;
import es.hack4edu.medidas.aplicacion.GestionarMedidas;
import es.hack4edu.medidas.dominio.Medidas;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Adaptador de entrada REST del seguimiento de medidas.
 * Cada usuario solo ve y gestiona las medidas de su ámbito (ver ControlAcceso). Registrar o cambiar el estado
 * exige además rol docente (la dirección también lo tiene) y la cabecera anti-CSRF.
 */
@Path("/api/medidas")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class MedidasResource {

    public record CambioEstado(String estado) {
    }

    @Inject
    GestionarMedidas medidas;

    @Inject
    SecurityIdentity identidad;

    @Inject
    ControlAcceso acceso;

    @GET
    @Path("/indicadores")
    public List<Medidas.IndicadorInfo> indicadores() {
        return medidas.indicadores();
    }

    /** Periodos reales en los que puede empezar una medida (se excluye la línea base sintética). */
    @GET
    @Path("/periodos")
    public List<Medidas.Periodo> periodos() {
        return medidas.periodos();
    }

    /** Medidas del centro; con grupo, las de ese grupo y las que afectan a todo el centro. */
    @GET
    public Medidas.Listado listar(@QueryParam("centro") Integer centro, @QueryParam("grupo") Integer grupo) {
        acceso.comprobar(centro, grupo);
        return medidas.listar(centro, grupo);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("docente")
    public Response crear(Medidas.NuevaMedida nueva) {
        if (nueva != null) {
            acceso.comprobar(nueva.centro(), nueva.grupo());
        }
        Medidas.Medida creada = medidas.crear(nueva, identidad.getPrincipal().getName());
        return Response.status(Response.Status.CREATED).entity(creada).build();
    }

    @PUT
    @Path("/{id}/estado")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("docente")
    public Medidas.Medida cambiarEstado(@PathParam("id") int id, CambioEstado cambio) {
        Medidas.Medida actual = medidas.obtener(id);
        acceso.comprobar(actual.centroId(), actual.grupoId());
        return medidas.cambiarEstado(id, cambio == null ? null : cambio.estado());
    }
}
