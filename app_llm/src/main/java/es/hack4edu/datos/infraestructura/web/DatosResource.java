package es.hack4edu.datos.infraestructura.web;

import es.hack4edu.compartido.dominio.Ambito;
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

    /** Centros y grupos para los selectores. */
    @GET
    @Path("/ambitos")
    public List<Agregados.Centro> ambitos() {
        return datos.ambitos();
    }

    /** Indicadores del ámbito pedido y, para comparar, los globales. */
    @GET
    @Path("/resumen")
    public Agregados.ResumenAmbito resumen(@QueryParam("centro") Integer centro, @QueryParam("grupo") Integer grupo) {
        return datos.resumen(Ambito.de(centro, grupo));
    }

    /** Resumen de todos los centros (comparativa). */
    @GET
    @Path("/centros")
    public List<Agregados.Resumen> centros() {
        return datos.centros();
    }

    /** Ranking de intervenciones del ámbito, con su desglose. */
    @GET
    @Path("/prioridades")
    public List<Agregados.Prioridad> prioridades(@QueryParam("centro") Integer centro, @QueryParam("grupo") Integer grupo) {
        return datos.prioridades(Ambito.de(centro, grupo));
    }

    /** Brecha de todos los factores (accionables y contextuales) en el ámbito. */
    @GET
    @Path("/brechas")
    public List<Agregados.Brecha> brechas(@QueryParam("centro") Integer centro, @QueryParam("grupo") Integer grupo) {
        return datos.brechas(Ambito.de(centro, grupo));
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
        return datos.nivelesDeFactor(codigo, Ambito.de(centro, grupo));
    }

    /** Valores ausentes por columna. */
    @GET
    @Path("/calidad")
    public List<Agregados.Calidad> calidad() {
        return datos.calidad();
    }
}
