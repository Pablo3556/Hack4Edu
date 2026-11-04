package es.hack4edu.datos.aplicacion;

import es.hack4edu.compartido.dominio.Ambito;
import es.hack4edu.compartido.dominio.ErrorAplicacion;
import es.hack4edu.datos.aplicacion.puertos.RepositorioAgregados;
import es.hack4edu.datos.dominio.Agregados;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

/** Casos de uso de consulta de indicadores agregados. Lo usan también otros módulos. */
@ApplicationScoped
public class ConsultaAgregados {

    @Inject
    RepositorioAgregados repositorio;

    public List<Agregados.Centro> ambitos() {
        return repositorio.ambitos();
    }

    /** Indicadores del ámbito y, para comparar, los globales. */
    public Agregados.ResumenAmbito resumen(Ambito ambito) {
        Agregados.Resumen r = repositorio.resumen(ambito).orElseThrow(() -> ErrorAplicacion.noEncontrado(
                "Sin datos para ese ámbito (no existe o tiene menos de 10 alumnos). ¿Están cargados los datos?"));
        return new Agregados.ResumenAmbito(r, resumenGlobal().orElse(null));
    }

    /** Indicadores del ámbito si existe y supera el umbral; vacío en otro caso. */
    public Optional<Agregados.Resumen> buscarResumen(Ambito ambito) {
        return repositorio.resumen(ambito);
    }

    public Optional<Agregados.Resumen> resumenGlobal() {
        return repositorio.resumen(Ambito.GLOBAL);
    }

    public List<Agregados.Resumen> centros() {
        return repositorio.resumenDeCentros();
    }

    public List<Agregados.Resumen> centroYGrupos(int centro) {
        return repositorio.resumenDelCentroYSusGrupos(centro);
    }

    public List<Agregados.Prioridad> prioridades(Ambito ambito) {
        return repositorio.prioridades(ambito);
    }

    public List<Agregados.Brecha> brechas(Ambito ambito) {
        return repositorio.brechas(ambito);
    }

    public List<Agregados.Factor> factores() {
        return repositorio.factores();
    }

    public List<Agregados.NivelFactor> nivelesDeFactor(String codigo, Ambito ambito) {
        return repositorio.nivelesDeFactor(codigo, ambito);
    }

    public List<Agregados.Calidad> calidad() {
        return repositorio.calidad();
    }

    /** Nombre del ámbito si existe. */
    public Optional<String> buscarNombreAmbito(Ambito ambito) {
        return repositorio.nombreAmbito(ambito);
    }

    /** Nombre del ámbito; falla con 404 si el centro o el grupo no existen. */
    public String nombreAmbito(Ambito ambito) {
        return repositorio.nombreAmbito(ambito)
                .orElseThrow(() -> ErrorAplicacion.noEncontrado("Ese centro o grupo no existe."));
    }
}
