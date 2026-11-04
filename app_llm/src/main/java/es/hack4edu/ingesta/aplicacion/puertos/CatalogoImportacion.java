package es.hack4edu.ingesta.aplicacion.puertos;

import es.hack4edu.ingesta.dominio.Importacion;

import java.util.List;
import java.util.Optional;

/** Puerto de salida: catálogos y auditoría que consulta la importación (solo lectura). */
public interface CatalogoImportacion {

    /** Periodos a los que se puede importar (la línea base sintética no). */
    List<Importacion.Periodo> periodos();

    /** "Centro · Grupo" si el grupo pertenece al centro. */
    Optional<String> nombreDelGrupo(int grupo, int centro);

    /** Nombre del periodo si existe y admite importaciones. */
    Optional<String> nombreDelPeriodoImportable(int periodo);

    /** Últimas 20 importaciones de docentes: las del usuario, o todas. */
    List<Importacion.Carga> historial(boolean todas, String usuario);
}
