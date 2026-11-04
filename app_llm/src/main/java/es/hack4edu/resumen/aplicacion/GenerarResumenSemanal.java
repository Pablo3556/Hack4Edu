package es.hack4edu.resumen.aplicacion;

import es.hack4edu.compartido.dominio.Ambito;
import es.hack4edu.compartido.dominio.ErrorAplicacion;
import es.hack4edu.datos.aplicacion.ConsultaAgregados;
import es.hack4edu.datos.dominio.Agregados;
import es.hack4edu.evolucion.aplicacion.ConsultarEvolucion;
import es.hack4edu.evolucion.dominio.InformeEvolucion;
import es.hack4edu.evolucion.dominio.MotorAlertas;
import es.hack4edu.medidas.aplicacion.GestionarMedidas;
import es.hack4edu.medidas.dominio.Medidas;
import es.hack4edu.resumen.dominio.ResumenSemanal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Caso de uso: resumen semanal de un grupo (o centro). Las tres prioridades del momento, en lenguaje llano y
 * con la fuente de cada cifra, más las señales de evolución y las medidas en curso.
 *
 * Es determinista: todo sale de indicadores agregados y de las medidas registradas, sin pasar por el modelo
 * de lenguaje, de modo que cada afirmación es verificable.
 */
@ApplicationScoped
public class GenerarResumenSemanal {

    @Inject
    ConsultaAgregados datos;

    @Inject
    ConsultarEvolucion evolucion;

    @Inject
    GestionarMedidas medidas;

    public ResumenSemanal.Resumen resumen(Ambito ambito) {
        if (ambito.centro() == null) {
            throw ErrorAplicacion.peticionInvalida("Elige un centro (y un grupo para el resumen de aula).");
        }
        InformeEvolucion.Evolucion evo = evolucion.evolucion(ambito);
        String nombre = evo.ambito();

        List<Medidas.Medida> todas = medidas.listar(ambito.centro(), ambito.grupo()).medidas();
        List<ResumenSemanal.MedidaBreve> enCurso = todas.stream().filter(m -> "en_curso".equals(m.estado()))
                .map(m -> new ResumenSemanal.MedidaBreve(m.id(), m.titulo(), m.grupo(), m.indicadorNombre(),
                        m.periodoInicioNombre(), m.evaluacion().veredicto(), m.evaluacion().mensaje())).toList();

        List<ResumenSemanal.Prioridad> prioridades = datos.prioridades(ambito).stream()
                .filter(p -> p.prioridad() != null).limit(3)
                .map(p -> prioridad(p, nombre, todas)).toList();

        List<ResumenSemanal.Senal> senales = evo.alertas().stream()
                .sorted(Comparator.comparingInt((MotorAlertas.Alerta a) -> a.nivel().ordinal()))
                .limit(3)
                .map(a -> new ResumenSemanal.Senal(a.nivel().name(), a.mensaje(), a.contexto(),
                        new ResumenSemanal.Fuente("analitica.vw_evolucion", nombre, a.periodo()
                                + (a.periodoReferencia() == null ? "" : " frente a " + a.periodoReferencia()))))
                .toList();

        boolean hayDatos = !prioridades.isEmpty() || !evo.serie().isEmpty();
        String mensaje = hayDatos ? null : "Sin datos suficientes en este ámbito (se necesitan al menos 10 alumnos).";
        if (hayDatos && prioridades.isEmpty()) {
            mensaje = "No hay prioridades calculables: ningún factor accionable llega al umbral de 10 alumnos por nivel.";
        }
        LocalDate hoy = LocalDate.now();
        String semana = "Semana " + hoy.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + " de " + hoy.get(IsoFields.WEEK_BASED_YEAR);
        return new ResumenSemanal.Resumen(nombre, ambito.grupo() != null ? "grupo" : "centro", semana, hoy.toString(),
                evo.periodoActual(), hayDatos, mensaje, prioridades, senales, enCurso, ResumenSemanal.AVISO);
    }

    private static ResumenSemanal.Prioridad prioridad(Agregados.Prioridad p, String ambito, List<Medidas.Medida> medidas) {
        return new ResumenSemanal.Prioridad(p.puesto(), p.factor(), p.factorNombre(),
                p.factorNombre() + ": " + n(p.pctPeor()) + " % del alumnado está en «" + p.nivelPeor() + "»",
                "Su nota media es " + n(p.notaPeor()) + " frente a " + n(p.notaMejor()) + " en «" + p.nivelMejor()
                        + "» (brecha de " + n(p.brecha()) + " puntos, " + p.magnitud() + ").",
                p.palanca() == null ? null : "Línea a explorar con el equipo: " + p.palanca(),
                p.magnitud(), p.fiabilidad(),
                new ResumenSemanal.Fuente("analitica.vw_prioridades", ambito,
                        "puesto " + p.puesto() + " · fiabilidad " + p.fiabilidad()),
                medidas.stream().filter(m -> "en_curso".equals(m.estado()) && p.factor().equals(m.factor()))
                        .map(Medidas.Medida::titulo).toList());
    }

    private static String n(Double v) {
        return v == null ? "–" : String.format(Locale.forLanguageTag("es-ES"), "%.1f", v);
    }
}
