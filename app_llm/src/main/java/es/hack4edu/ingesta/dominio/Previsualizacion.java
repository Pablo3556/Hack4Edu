package es.hack4edu.ingesta.dominio;

import java.time.Instant;
import java.util.UUID;

/**
 * Importación pendiente de confirmación: el archivo ya leído y su último informe de validación.
 * Vive solo en memoria y caduca a los pocos minutos.
 */
public final class Previsualizacion {

    public final String token = UUID.randomUUID().toString();
    public final String usuario;
    public final int cargaId;
    public final String archivo;
    public final int grupoId;
    public final int periodoId;
    public final String ambito;
    public final String periodo;
    public final LectorCsv.Archivo csv;
    public final Instant creada = Instant.now();
    public volatile ValidadorIngesta.Informe informe;
    public volatile Instant expira;

    public Previsualizacion(String usuario, int cargaId, String archivo, int grupoId, int periodoId, String ambito,
                            String periodo, LectorCsv.Archivo csv, ValidadorIngesta.Informe informe, Instant expira) {
        this.usuario = usuario;
        this.cargaId = cargaId;
        this.archivo = archivo;
        this.grupoId = grupoId;
        this.periodoId = periodoId;
        this.ambito = ambito;
        this.periodo = periodo;
        this.csv = csv;
        this.informe = informe;
        this.expira = expira;
    }
}
