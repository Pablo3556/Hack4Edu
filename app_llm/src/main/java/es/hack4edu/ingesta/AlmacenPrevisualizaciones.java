package es.hack4edu.ingesta;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vistas previas pendientes de confirmación. Viven SOLO en memoria (nunca en disco)
 * y caducan a los pocos minutos: así el archivo del docente no se conserva más de lo
 * necesario. Al caducar o cancelarse, la carga queda auditada como tal.
 */
@ApplicationScoped
public class AlmacenPrevisualizaciones {

    static final int MAX_POR_USUARIO = 3;
    static final int MAX_TOTAL = 30;

    public static final class Previsualizacion {
        final String token = UUID.randomUUID().toString();
        final String usuario;
        final int cargaId;
        final String archivo;
        final int grupoId;
        final int periodoId;
        final String ambito;
        final String periodo;
        final LectorCsv.Archivo csv;
        final Instant creada = Instant.now();
        volatile ValidadorIngesta.Informe informe;
        volatile Instant expira;

        Previsualizacion(String usuario, int cargaId, String archivo, int grupoId, int periodoId, String ambito,
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

    private final Map<String, Previsualizacion> pendientes = new ConcurrentHashMap<>();

    @ConfigProperty(name = "ingesta.minutos-vista-previa", defaultValue = "20")
    int minutos;

    @Inject
    RepositorioIngesta repositorio;

    public Previsualizacion guardar(String usuario, int cargaId, String archivo, int grupoId, int periodoId,
                                    String ambito, String periodo, LectorCsv.Archivo csv,
                                    ValidadorIngesta.Informe informe) {
        limpiar();
        // Cada docente tiene como mucho unas pocas vistas previas abiertas
        List<Previsualizacion> suyas = pendientes.values().stream().filter(p -> p.usuario.equals(usuario))
                .sorted(Comparator.comparing(p -> p.creada)).toList();
        for (int i = 0; i <= suyas.size() - MAX_POR_USUARIO; i++) {
            cerrar(suyas.get(i), "caducada", "Sustituida por una vista previa más reciente del mismo usuario.");
        }
        if (pendientes.size() >= MAX_TOTAL) {
            throw new IllegalStateException("Hay demasiadas importaciones pendientes de confirmar. "
                    + "Inténtalo de nuevo en unos minutos.");
        }
        Previsualizacion p = new Previsualizacion(usuario, cargaId, archivo, grupoId, periodoId, ambito, periodo,
                csv, informe, Instant.now().plus(Duration.ofMinutes(minutos)));
        pendientes.put(p.token, p);
        return p;
    }

    /** La vista previa del token, solo si pertenece al usuario y no ha caducado. */
    public Optional<Previsualizacion> obtener(String token, String usuario) {
        limpiar();
        Previsualizacion p = token == null ? null : pendientes.get(token);
        if (p == null || !p.usuario.equals(usuario)) {
            return Optional.empty();
        }
        p.expira = Instant.now().plus(Duration.ofMinutes(minutos));
        return Optional.of(p);
    }

    /** Saca la vista previa del almacén (tras confirmar). */
    public void quitar(Previsualizacion p) {
        pendientes.remove(p.token);
    }

    /** Saca la vista previa y deja constancia en la auditoría. */
    public void cerrar(Previsualizacion p, String estado, String resultado) {
        if (pendientes.remove(p.token) == null) {
            return;
        }
        try {
            repositorio.actualizarCarga(p.cargaId, estado, null, null, resultado);
        } catch (Exception e) {
            Log.warnf("Ingesta: no se pudo marcar la carga %d como %s: %s", p.cargaId, estado, e.getMessage());
        }
    }

    void limpiar() {
        Instant ahora = Instant.now();
        pendientes.values().stream().filter(p -> p.expira.isBefore(ahora)).toList()
                .forEach(p -> cerrar(p, "caducada",
                        "La vista previa caducó sin confirmarse; los datos se descartaron."));
    }
}
