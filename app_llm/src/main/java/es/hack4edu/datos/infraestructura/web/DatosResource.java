package es.hack4edu.datos.infraestructura.web;

import es.hack4edu.compartido.infraestructura.web.ControlAcceso;
import es.hack4edu.datos.aplicacion.ConsultaAgregados;
import es.hack4edu.datos.dominio.Agregados;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Adaptador de entrada REST (solo lectura) para la interfaz web.
 * Ámbito de cada consulta: sin parámetros = global; ?centro=3 = centro; ?centro=3&grupo=10 = grupo.
 */
@Path("/api/datos")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class DatosResource {

    @Inject
    ConsultaAgregados datos;

    @Inject
    ControlAcceso acceso;

    /** Centros y grupos para los selectores. */
    @GET
    @Path("/ambitos")
    public List<Agregados.Centro> ambitos() {
        ControlAcceso.Permiso p = acceso.permiso();
        if (p.todosLosCentros()) {
            return datos.ambitos();
        }
        // Dirección: su centro con todos sus grupos. Docente: su centro con solo su grupo.
        return datos.ambitos().stream()
                .filter(c -> c.id() == p.centro())
                .map(c -> p.grupo() == null ? c : new Agregados.Centro(c.id(), c.nombre(), c.tipo(),
                        c.grupos().stream().filter(g -> g.id() == p.grupo()).toList()))
                .toList();
    }

    /** Indicadores del ámbito pedido y, para comparar, los globales. */
    @GET
    @Path("/resumen")
    public Agregados.ResumenAmbito resumen(@QueryParam("centro") Integer centro, @QueryParam("grupo") Integer grupo) {
        return datos.resumen(acceso.comprobar(centro, grupo));
    }

    /** Resumen de todos los centros (comparativa). */
    @GET
    @Path("/centros")
    public List<Agregados.Resumen> centros() {
        acceso.exigirTodosLosCentros();
        return datos.centros();
    }

    /** Ranking de intervenciones del ámbito, con su desglose. */
    @GET
    @Path("/prioridades")
    public List<Agregados.Prioridad> prioridades(@QueryParam("centro") Integer centro, @QueryParam("grupo") Integer grupo) {
        return datos.prioridades(acceso.comprobar(centro, grupo));
    }

    /** Brecha de todos los factores (accionables y contextuales) en el ámbito. */
    @GET
    @Path("/brechas")
    public List<Agregados.Brecha> brechas(@QueryParam("centro") Integer centro, @QueryParam("grupo") Integer grupo) {
        return datos.brechas(acceso.comprobar(centro, grupo));
    }

    /** Catálogo de factores para el selector. */
    @GET
    @Path("/factores")
    public List<Agregados.Factor> factores() {
        return datos.factores();
    }

    /** Nota media por nivel de un factor en el ámbito. Vacío si el factor está oculto por el umbral. */
    @GET
    @Path("/factor/{codigo}")
    public List<Agregados.NivelFactor> factor(@PathParam("codigo") String codigo, @QueryParam("centro") Integer centro,
                                              @QueryParam("grupo") Integer grupo) {
        return datos.nivelesDeFactor(codigo, acceso.comprobar(centro, grupo));
    }

    /** Valores ausentes por columna. */
    @GET
    @Path("/calidad")
    public List<Agregados.Calidad> calidad() {
        return datos.calidad();
    }
}
