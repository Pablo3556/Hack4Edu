package es.hack4edu.evolucion.aplicacion;

import es.hack4edu.compartido.dominio.Ambito;
import es.hack4edu.compartido.dominio.ErrorAplicacion;
import es.hack4edu.datos.aplicacion.ConsultaAgregados;
import es.hack4edu.datos.dominio.Agregados;
import es.hack4edu.evolucion.aplicacion.puertos.RepositorioEvolucion;
import es.hack4edu.evolucion.dominio.InformeEvolucion;
import es.hack4edu.evolucion.dominio.MotorAlertas;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Caso de uso: evolución agregada de un grupo o centro por periodos, con alertas tempranas.
 * Solo trabaja con indicadores agregados (umbral mínimo de grupo).
 */
@ApplicationScoped
public class ConsultarEvolucion {

    static final String AVISO = "Indicadores agregados de grupos con al menos 10 registros. Las alertas son "
            + "informativas: describen cambios en medias y porcentajes, no señalan ni clasifican a ningún alumno "
            + "y no son predicciones.";

    @Inject
    RepositorioEvolucion repositorio;

    @Inject
    ConsultaAgregados datos;

    public InformeEvolucion.Evolucion evolucion(Ambito ambito) {
        if (ambito.centro() == null) {
            throw ErrorAplicacion.peticionInvalida("Elige un centro (rol Dirección) o un grupo (rol Docente).");
        }
        int centro = ambito.centro();
        boolean esGrupo = ambito.grupo() != null;
        String nombre = datos.nombreAmbito(ambito);
        String nombreCentro = datos.nombreAmbito(Ambito.de(centro, null));

        List<MotorAlertas.Punto> serie = repositorio.serie(centro, ambito.grupo());
        List<MotorAlertas.Punto> serieCentro = esGrupo ? repositorio.serie(centro, null) : List.of();
        Map<String, List<MotorAlertas.Punto>> grupos = repositorio.seriesDeLosGrupos(centro);

        MotorAlertas.Referencia referencia = datos.resumenGlobal()
                .map(g -> new MotorAlertas.Referencia("conjunto de centros", g.notaMedia(), g.asistenciaMedia()))
                .orElse(null);
        MotorAlertas.PrioridadPrincipal prioridad = datos.prioridades(ambito).stream()
                .filter(p -> p.prioridad() != null).findFirst()
                .map(p -> new MotorAlertas.PrioridadPrincipal(p.factorNombre(), p.nivelPeor(), p.pctPeor(), p.brecha()))
                .orElse(null);

        MotorAlertas.Resultado r = MotorAlertas.calcular(new MotorAlertas.Entrada(nombre, serie, grupos, nombreCentro,
                referencia, prioridad));

        MotorAlertas.Punto actual = serie.isEmpty() ? null : serie.get(serie.size() - 1);
        MotorAlertas.Punto anterior = serie.size() < 2 ? null : serie.get(serie.size() - 2);
        String ultima = serie.stream().map(MotorAlertas.Punto::actualizado).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);

        return new InformeEvolucion.Evolucion(nombre, esGrupo ? "grupo" : "centro", serie, serieCentro,
                esGrupo ? Map.of() : grupos, actual == null ? null : actual.periodo(),
                anterior == null ? null : anterior.periodo(), comparaciones(actual, anterior), r.datosSuficientes(),
                r.mensajeDatos(), r.alertas(), r.orientacion(), ultima, AVISO);
    }

    static List<InformeEvolucion.Comparacion> comparaciones(MotorAlertas.Punto a, MotorAlertas.Punto b) {
        if (a == null) {
            return List.of();
        }
        List<InformeEvolucion.Comparacion> c = new ArrayList<>();
        c.add(comparacion("n_alumnos", "Registros", "", b == null ? null : (double) b.nAlumnos(), (double) a.nAlumnos(), true));
        c.add(comparacion("nota_media", "Nota media", "pts", b == null ? null : b.notaMedia(), a.notaMedia(), true));
        c.add(comparacion("asistencia_media", "Asistencia media", "%", b == null ? null : b.asistenciaMedia(),
                a.asistenciaMedia(), true));
        c.add(comparacion("pct_asistencia_baja", "Asisten menos del 80 %", "%", b == null ? null : b.pctAsistenciaBaja(),
                a.pctAsistenciaBaja(), false));
        c.add(comparacion("horas_estudio_media", "Horas de estudio (semana)", "h", b == null ? null : b.horasEstudioMedia(),
                a.horasEstudioMedia(), true));
        c.add(comparacion("tutorias_media", "Tutorías al mes", "", b == null ? null : b.tutoriasMedia(),
                a.tutoriasMedia(), true));
        return c;
    }

    private static InformeEvolucion.Comparacion comparacion(String codigo, String nombre, String unidad, Double antes,
                                                            Double ahora, boolean mejorSiSube) {
        Double var = antes == null || ahora == null ? null : Math.round((ahora - antes) * 100) / 100.0;
        return new InformeEvolucion.Comparacion(codigo, nombre, unidad, antes, ahora, var, mejorSiSube);
    }
}
