package es.hack4edu.direccion.aplicacion.puertos;

import es.hack4edu.direccion.dominio.ProgresoDocente;

import java.util.List;

/** Puerto de salida: progreso del alumnado por grupo y por docente (vistas agregadas, con umbral mínimo). */
public interface RepositorioProgreso {

    record FilaGrupo(Integer grupoId, String nombre, int nAlumnos, String tutor, int nDocentes, Integer horasSemana,
                     Double experienciaMedia, Double formacionMedia, ProgresoDocente.Medicion medicion) {
    }

    record FilaDocente(String codigo, int aniosExperiencia, String nivelFormacion, int horasFormacionAnio,
                       String tipoContrato, Integer horasSemana, Integer nGrupos, String grupos, String asignaturas,
                       Integer nAlumnos, ProgresoDocente.Medicion medicion) {
    }

    /** Fila del centro (grupoId null) y de sus grupos que superan el umbral. */
    List<FilaGrupo> grupos(int centro);

    /** Docentes del centro, por código (nunca ordenados por resultado). */
    List<FilaDocente> docentes(int centro);
}
