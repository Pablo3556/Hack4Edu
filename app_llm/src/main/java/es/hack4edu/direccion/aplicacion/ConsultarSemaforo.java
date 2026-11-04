package es.hack4edu.direccion.aplicacion;

import es.hack4edu.compartido.dominio.Ambito;
import es.hack4edu.compartido.dominio.ErrorAplicacion;
import es.hack4edu.datos.aplicacion.ConsultaAgregados;
import es.hack4edu.datos.dominio.Agregados;
import es.hack4edu.direccion.dominio.PanelDireccion;
import es.hack4edu.direccion.dominio.Semaforo;
import es.hack4edu.medidas.aplicacion.GestionarMedidas;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Caso de uso: panel de dirección (semáforo por grupo del centro y brechas de equidad). */
@ApplicationScoped
public class ConsultarSemaforo {

    @Inject
    ConsultaAgregados datos;

    @Inject
    GestionarMedidas medidas;

    public PanelDireccion.Panel semaforo(Integer centro) {
        if (centro == null) {
            throw ErrorAplicacion.peticionInvalida("Indica el centro.");
        }
        Ambito delCentro = Ambito.de(centro, null);
        String nombre = datos.buscarNombreAmbito(delCentro)
                .orElseThrow(() -> ErrorAplicacion.noEncontrado("Ese centro no existe."));
        Agregados.Resumen global = datos.resumenGlobal()
                .orElseThrow(() -> ErrorAplicacion.noEncontrado("Todavía no hay datos cargados."));
        Map<Integer, Integer> enCurso = medidas.enCursoPorGrupo(centro);

        PanelDireccion.Fila filaCentro = null;
        List<PanelDireccion.Fila> grupos = new ArrayList<>();
        List<Integer> conDatos = new ArrayList<>();
        for (Agregados.Resumen r : datos.centroYGrupos(centro)) {
            boolean esCentro = "centro".equals(r.ambito());
            PanelDireccion.Fila f = fila(r, global, esCentro ? 0 : enCurso.getOrDefault(r.grupoId(), 0));
            if (esCentro) {
                filaCentro = f;
            } else {
                grupos.add(f);
                conDatos.add(r.grupoId());
            }
        }
        List<String> sinDatos = datos.ambitos().stream().filter(c -> c.id() == centro)
                .flatMap(c -> c.grupos().stream()).filter(g -> !conDatos.contains(g.id()))
                .map(Agregados.Grupo::nombre).toList();

        Map<String, Integer> recuento = new LinkedHashMap<>();
        recuento.put(Semaforo.VERDE, 0);
        recuento.put(Semaforo.AMBAR, 0);
        recuento.put(Semaforo.ROJO, 0);
        grupos.stream().map(PanelDireccion.Fila::color).filter(Objects::nonNull)
                .forEach(c -> recuento.merge(c, 1, Integer::sum));

        List<PanelDireccion.Equidad> equidad = datos.brechas(delCentro).stream()
                .filter(b -> "contextual".equals(b.tipo()))
                .sorted(Comparator.comparing((Agregados.Brecha b) ->
                        b.brechaEstandarizada() == null ? null : Math.abs(b.brechaEstandarizada()),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(b -> new PanelDireccion.Equidad(b.factor(), b.factorNombre(), b.nivelPeor(), b.nivelMejor(),
                        b.pctPeor(), b.brecha(), b.brechaEstandarizada(), Semaforo.colorBrecha(b.brechaEstandarizada()),
                        b.fiabilidad())).toList();

        List<PanelDireccion.Indicador> indicadores = Semaforo.CRITERIOS.stream()
                .map(c -> new PanelDireccion.Indicador(c.codigo(), c.nombre(), c.unidad(), c.mejorSiSube())).toList();
        return new PanelDireccion.Panel(nombre, indicadores, filaCentro, grupos, sinDatos, recuento, equidad,
                PanelDireccion.CRITERIO, PanelDireccion.AVISO);
    }

    private static PanelDireccion.Fila fila(Agregados.Resumen r, Agregados.Resumen ref, int medidas) {
        List<PanelDireccion.Celda> celdas = new ArrayList<>();
        for (Semaforo.Criterio c : Semaforo.CRITERIOS) {
            Double v = valor(r, c.codigo());
            Double base = valor(ref, c.codigo());
            Double dif = v == null || base == null ? null : Math.round((v - base) * 100) / 100.0;
            celdas.add(new PanelDireccion.Celda(c.codigo(), v, base, dif, Semaforo.colorIndicador(c, v, base)));
        }
        String color = Semaforo.peor(celdas.stream().map(PanelDireccion.Celda::color).toList());
        return new PanelDireccion.Fila(r.grupoId(), "centro".equals(r.ambito()) ? r.centro() : r.grupo(),
                r.nAlumnos(), color, celdas, medidas);
    }

    static Double valor(Agregados.Resumen r, String codigo) {
        return switch (codigo) {
            case "nota_media" -> r.notaMedia();
            case "asistencia_media" -> r.asistenciaMedia();
            case "pct_asistencia_baja" -> r.pctAsistenciaBaja();
            case "pct_recursos_bajos" -> r.pctRecursosBajos();
            case "tutorias_media" -> r.tutoriasMedia();
            default -> null;
        };
    }
}
