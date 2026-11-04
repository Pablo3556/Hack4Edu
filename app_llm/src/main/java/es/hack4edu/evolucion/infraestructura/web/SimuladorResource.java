package es.hack4edu.evolucion.infraestructura.web;

import es.hack4edu.compartido.dominio.Ambito;
import es.hack4edu.evolucion.aplicacion.SimularEscenario;
import es.hack4edu.evolucion.dominio.CatalogoSimulador;
import es.hack4edu.evolucion.dominio.Simulador;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/** Adaptador de entrada REST del simulador "¿Qué pasaría si…?". */
@Path("/api/simulador")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class SimuladorResource {

    @Inject
    SimularEscenario simulador;

    /** Factores accionables con sus niveles y si se pueden simular en el ámbito elegido. */
    @GET
    @Path("/factores")
    public List<CatalogoSimulador.FactorSimulable> factores(@QueryParam("centro") Integer centro,
                                                            @QueryParam("grupo") Integer grupo) {
        return simulador.factores(Ambito.de(centro, grupo));
    }

    /** Ej.: /api/simulador?centro=3&grupo=10&factor=tutorias&desde=2&hacia=3&porcentaje=100 */
    @GET
    public Simulador.Resultado simular(@QueryParam("centro") Integer centro, @QueryParam("grupo") Integer grupo,
                                       @QueryParam("factor") String factor, @QueryParam("desde") Integer desde,
                                       @QueryParam("hacia") Integer hacia, @QueryParam("porcentaje") Integer porcentaje) {
        return simulador.simular(Ambito.de(centro, grupo), factor, desde, hacia, porcentaje);
    }
}
