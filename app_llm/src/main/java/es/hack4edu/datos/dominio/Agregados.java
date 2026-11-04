package es.hack4edu.datos.dominio;

import java.util.List;

/**
 * Modelo de lectura de los datos agregados (vistas del esquema "analitica").
 * Nunca contiene datos de un alumno: cada cifra sale de grupos de al menos k_min alumnos.
 */
public final class Agregados {

    private Agregados() {
    }

    public record Grupo(int id, String nombre) {
    }

    public record Centro(int id, String nombre, String tipo, List<Grupo> grupos) {
    }

    public record Resumen(String ambito, Integer centroId, String centro, String tipoCentro, Integer grupoId,
                          String grupo, Integer nAlumnos, Double notaMedia, Double notaP25, Double notaMediana,
                          Double notaP75, Double difVsGlobal, Double asistenciaMedia, Double pctAsistenciaBaja,
                          Double horasEstudioMedia, Double tutoriasMedia, Double pctRecursosBajos,
                          Double pctCalidadDocenteBaja, Double pctExtraescolares, Double pctImplicacionFamiliarBaja) {
    }

    public record ResumenAmbito(Resumen ambito, Resumen global) {
    }

    public record Prioridad(Integer puesto, String factor, String factorNombre, String dimension, String nivelPeor,
                            Double pctPeor, Double notaPeor, String nivelMejor, Double notaMejor, Double brecha,
                            Double brechaEstandarizada, String magnitud, Double accionabilidad, Double prioridad,
                            String fiabilidad, String palanca, String aviso) {
    }

    public record Brecha(String factor, String factorNombre, String tipo, String dimension, String nivelPeor,
                         String nivelMejor, Double pctPeor, Double brecha, Double brechaEstandarizada,
                         String fiabilidad) {
    }

    public record Factor(String codigo, String nombre, String tipo, String dimension, String descripcion) {
    }

    public record NivelFactor(Integer orden, String nivel, Integer n, Double pctAmbito, Double notaMedia,
                              Double difVsMediaAmbito, Double asistenciaMedia, Double pctSinDato) {
    }

    public record Calidad(String columna, Integer nAusentes, Double pctAusentes, Integer nTotal) {
    }
}
