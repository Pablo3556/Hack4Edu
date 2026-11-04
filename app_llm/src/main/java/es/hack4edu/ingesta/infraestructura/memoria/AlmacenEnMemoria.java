package es.hack4edu.ingesta.infraestructura.memoria;

import es.hack4edu.ingesta.aplicacion.puertos.AlmacenVistasPrevias;
import es.hack4edu.ingesta.aplicacion.puertos.RepositorioCargas;
import es.hack4edu.ingesta.dominio.LectorCsv;
import es.hack4edu.ingesta.dominio.Previsualizacion;
import es.hack4edu.ingesta.dominio.ValidadorIngesta;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptador de salida: vistas previas pendientes de confirmación. Viven SOLO en memoria (nunca en disco)
 * y caducan a los pocos minutos: así el archivo del docente no se conserva más de lo necesario.
 * Al caducar o cancelarse, la carga queda auditada como tal.
 */
@ApplicationScoped
public class AlmacenEnMemoria implements AlmacenVistasPrevias {

    static final int MAX_POR_USUARIO = 3;
    static final int MAX_TOTAL = 30;

    private final Map<String, Previsualizacion> pendientes = new ConcurrentHashMap<>();

    @ConfigProperty(name = "ingesta.minutos-vista-previa", defaultValue = "20")
    int minutos;

    @Inject
    RepositorioCargas cargas;

    @Override
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

    @Override
    public Optional<Previsualizacion> obtener(String token, String usuario) {
        limpiar();
        Previsualizacion p = token == null ? null : pendientes.get(token);
        if (p == null || !p.usuario.equals(usuario)) {
            return Optional.empty();
        }
        p.expira = Instant.now().plus(Duration.ofMinutes(minutos));
        return Optional.of(p);
    }

    @Override
    public void quitar(Previsualizacion p) {
        pendientes.remove(p.token);
    }

    @Override
    public void cerrar(Previsualizacion p, String estado, String resultado) {
        if (pendientes.remove(p.token) == null) {
            return;
        }
        try {
            cargas.actualizarCarga(p.cargaId, estado, null, null, resultado);
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
