package es.hack4edu.ingesta.dominio;

import java.util.List;

/** Modelo de la importación de CSV por docentes (lo que se muestra y se audita). */
public final class Importacion {

    private Importacion() {
    }

    public record Periodo(int id, String codigo, String nombre, String fechaInicio, String fechaFin, boolean actual) {
    }

    /** Fila de la auditoría de cargas. */
    public record Carga(Integer id, String fecha, String actualizado, String usuario, String archivo, String centro,
                        String grupo, String periodo, Integer filasProcesadas, Integer filasValidas,
                        Integer filasError, Integer filasCargadas, String estado, String resultado) {
    }

    public record Resultado(int cargaId, String estado, int filasCargadas, String ambito, String periodo,
                            String mensaje) {
    }

    /** Grupo y periodo de destino ya comprobados. */
    public record Destino(String ambito, String periodo) {
    }

    public record VistaPrevia(String token, int cargaId, String archivo, String codificacion, String separador,
                              String ambito, String periodo, List<ValidadorIngesta.Columna> columnas,
                              ValidadorIngesta.Resumen resumen, String resumenTexto, List<String> problemasMapeo,
                              List<String> bloqueos, List<String> avisos,
                              List<ValidadorIngesta.Incidencia> incidencias, int incidenciasOmitidas,
                              ValidadorIngesta.Muestra muestra, boolean puedeConfirmar, String expira) {
    }
}
