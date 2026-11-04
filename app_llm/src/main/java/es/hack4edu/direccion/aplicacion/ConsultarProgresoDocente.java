package es.hack4edu.direccion.aplicacion;

import es.hack4edu.compartido.dominio.Ambito;
import es.hack4edu.compartido.dominio.ErrorAplicacion;
import es.hack4edu.datos.aplicacion.ConsultaAgregados;
import es.hack4edu.direccion.aplicacion.puertos.RepositorioProgreso;
import es.hack4edu.direccion.dominio.ProgresoDocente;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Caso de uso: ¿progresa el alumnado de cada grupo y de cada docente del centro como se espera? */
@ApplicationScoped
public class ConsultarProgresoDocente {

    @Inject
    RepositorioProgreso repositorio;

    @Inject
    ConsultaAgregados datos;

    public ProgresoDocente.Panel progreso(Integer centro) {
        if (centro == null) {
            throw ErrorAplicacion.peticionInvalida("Indica el centro.");
        }
        String nombre = datos.buscarNombreAmbito(Ambito.de(centro, null))
                .orElseThrow(() -> ErrorAplicacion.noEncontrado("Ese centro no existe."));

        ProgresoDocente.Grupo filaCentro = null;
        List<ProgresoDocente.Grupo> grupos = new ArrayList<>();
        for (RepositorioProgreso.FilaGrupo f : repositorio.grupos(centro)) {
            ProgresoDocente.Grupo g = new ProgresoDocente.Grupo(f.grupoId() == null ? 0 : f.grupoId(),
                    f.grupoId() == null ? nombre : f.nombre(), f.nAlumnos(), f.tutor(), f.nDocentes(),
                    f.horasSemana(), f.experienciaMedia(), f.formacionMedia(), ProgresoDocente.estimar(f.medicion()));
            if (f.grupoId() == null) {
                filaCentro = g;
            } else {
                grupos.add(g);
            }
        }

        List<ProgresoDocente.Docente> docentes = repositorio.docentes(centro).stream()
                .map(f -> new ProgresoDocente.Docente(f.codigo(), f.aniosExperiencia(), f.nivelFormacion(),
                        f.horasFormacionAnio(), f.tipoContrato(), f.horasSemana(), f.nGrupos(), f.grupos(),
                        f.asignaturas(), f.nAlumnos(), ProgresoDocente.estimar(f.medicion())))
                .toList();

        Map<String, Integer> recuento = new LinkedHashMap<>();
        recuento.put(ProgresoDocente.POR_ENCIMA, 0);
        recuento.put(ProgresoDocente.EN_LO_ESPERADO, 0);
        recuento.put(ProgresoDocente.POR_DEBAJO, 0);
        grupos.stream().map(g -> g.progreso().veredicto()).filter(v -> v != null)
                .forEach(v -> recuento.merge(v, 1, Integer::sum));

        return new ProgresoDocente.Panel(nombre, !docentes.isEmpty(), filaCentro, grupos, docentes, recuento,
                ProgresoDocente.METODO, ProgresoDocente.AVISO);
    }
}
