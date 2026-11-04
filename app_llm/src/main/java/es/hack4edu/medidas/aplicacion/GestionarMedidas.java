package es.hack4edu.medidas.aplicacion;

import es.hack4edu.compartido.dominio.ErrorAplicacion;
import es.hack4edu.medidas.aplicacion.puertos.RepositorioMedidas;
import es.hack4edu.medidas.dominio.EvaluadorMedida;
import es.hack4edu.medidas.dominio.Indicador;
import es.hack4edu.medidas.dominio.Medidas;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Casos de uso del seguimiento de medidas: registrar una medida, cambiar su estado y valorar su efecto
 * comparando el indicador antes y después, descontando lo que cambió en los demás grupos o centros.
 */
@ApplicationScoped
public class GestionarMedidas {

    @Inject
    RepositorioMedidas repositorio;

    public List<Medidas.IndicadorInfo> indicadores() {
        return Arrays.stream(Indicador.values())
                .map(i -> new Medidas.IndicadorInfo(i.codigo(), i.nombre(), i.unidad(), i.mejorSiSube())).toList();
    }

    public List<Medidas.Periodo> periodos() {
        return repositorio.periodosReales();
    }

    public Medidas.Listado listar(Integer centro, Integer grupo) {
        if (centro == null) {
            throw ErrorAplicacion.peticionInvalida("Indica el centro.");
        }
        return new Medidas.Listado(valorar(repositorio.buscar(centro, grupo)), Medidas.AVISO);
    }

    public Medidas.Medida crear(Medidas.NuevaMedida n, String usuario) {
        if (n == null || n.centro() == null) {
            throw ErrorAplicacion.peticionInvalida("Falta el centro.");
        }
        String titulo = n.titulo() == null ? "" : n.titulo().strip();
        String descripcion = n.descripcion() == null || n.descripcion().isBlank() ? null : n.descripcion().strip();
        if (titulo.length() < 3 || titulo.length() > 120) {
            throw ErrorAplicacion.peticionInvalida("El título debe tener entre 3 y 120 caracteres.");
        }
        if (descripcion != null && descripcion.length() > 500) {
            throw ErrorAplicacion.peticionInvalida("La descripción admite como máximo 500 caracteres.");
        }
        if (Indicador.de(n.indicador()).isEmpty()) {
            throw ErrorAplicacion.peticionInvalida("Elige el indicador que quieres vigilar.");
        }
        if (n.periodoInicio() == null
                || repositorio.periodosReales().stream().noneMatch(p -> p.id() == n.periodoInicio())) {
            throw ErrorAplicacion.peticionInvalida("Elige el periodo en que empieza la medida.");
        }
        if (!repositorio.existeCentro(n.centro())) {
            throw ErrorAplicacion.peticionInvalida("Ese centro no existe.");
        }
        if (n.grupo() != null && !repositorio.grupoPerteneceAlCentro(n.grupo(), n.centro())) {
            throw ErrorAplicacion.peticionInvalida("Ese grupo no pertenece al centro.");
        }
        String factor = n.factor() == null || n.factor().isBlank() ? null : n.factor();
        if (factor != null && !repositorio.existeFactor(factor)) {
            throw ErrorAplicacion.peticionInvalida("Ese factor no existe.");
        }
        int id = repositorio.insertar(new Medidas.NuevaMedida(n.centro(), n.grupo(), factor, titulo, descripcion,
                n.indicador(), n.periodoInicio()), usuario);
        return unica(id);
    }

    public Medidas.Medida cambiarEstado(int id, String estado) {
        if (estado == null || !Medidas.ESTADOS.contains(estado)) {
            throw ErrorAplicacion.peticionInvalida("Estado no válido (en_curso, finalizada o descartada).");
        }
        if (!repositorio.cambiarEstado(id, estado)) {
            throw ErrorAplicacion.noEncontrado("Esa medida no existe.");
        }
        return unica(id);
    }

    public Map<Integer, Integer> enCursoPorGrupo(int centro) {
        return repositorio.enCursoPorGrupo(centro);
    }

    // -----------------------------------------------------------------

    private Medidas.Medida unica(int id) {
        return valorar(List.of(repositorio.porId(id)
                .orElseThrow(() -> ErrorAplicacion.noEncontrado("Esa medida no existe.")))).get(0);
    }

    private List<Medidas.Medida> valorar(List<Medidas.MedidaRegistrada> registradas) {
        if (registradas.isEmpty()) {
            return List.of();
        }
        Map<Integer, String> nombres = repositorio.nombresDePeriodos();
        // Una consulta por tipo de ámbito y centro, compartida por todas las medidas del listado
        Map<String, List<Medidas.ValorPeriodo>> cache = new HashMap<>();
        List<Medidas.Medida> salida = new ArrayList<>();
        for (Medidas.MedidaRegistrada m : registradas) {
            boolean deGrupo = m.grupoId() != null;
            List<Medidas.ValorPeriodo> filas = cache.computeIfAbsent(deGrupo ? "g" + m.centroId() : "c",
                    k -> deGrupo ? repositorio.evolucionDeLosGrupos(m.centroId()) : repositorio.evolucionDeLosCentros());
            Map<Integer, Double> propio = new HashMap<>();
            Map<Integer, Map<Integer, Double>> pares = new LinkedHashMap<>();
            int propia = deGrupo ? m.grupoId() : m.centroId();
            for (Medidas.ValorPeriodo f : filas) {
                Double v = f.valores().get(m.indicador().codigo());
                if (v == null) {
                    continue;
                }
                int clave = deGrupo ? f.grupoId() : f.centroId();
                if (clave == propia) {
                    propio.put(f.orden(), v);
                } else {
                    pares.computeIfAbsent(clave, k -> new HashMap<>()).put(f.orden(), v);
                }
            }
            EvaluadorMedida.Evaluacion ev = EvaluadorMedida.evaluar(m.indicador(), propio,
                    List.copyOf(pares.values()), nombres, m.periodoOrden());
            salida.add(new Medidas.Medida(m.id(), m.centroId(), m.centro(), m.grupoId(), m.grupo(), m.factor(),
                    m.factorNombre(), m.titulo(), m.descripcion(), m.indicador().codigo(), m.indicador().nombre(),
                    m.indicador().unidad(), m.periodoInicio(), m.periodoNombre(), m.estado(), m.creadaPor(),
                    m.creada(), ev));
        }
        return salida;
    }
}
