package es.hack4edu.compartido.infraestructura.web;

import es.hack4edu.compartido.dominio.Ambito;
import es.hack4edu.compartido.dominio.ErrorAplicacion;
import es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

/**
 * Qué centro y grupo puede consultar cada usuario. Lo comprueba el servidor en cada petición:
 *   administracion → conjunto de centros y cualquier centro (no baja a grupos)
 *   direccion      → su centro y los grupos de su centro
 *   docente        → solo su grupo
 * El centro y el grupo de cada usuario vienen de la configuración (.env).
 */
@ApplicationScoped
public class ControlAcceso {

    /** Ámbito permitido al usuario: centro y grupo fijos, o null si puede ver cualquiera. */
    public record Permiso(String rol, Integer centro, Integer grupo) {

        public boolean todosLosCentros() {
            return "administracion".equals(rol);
        }
    }

    @Inject
    SecurityIdentity identidad;

    @Inject
    JdbcConsultas consultas;

    @ConfigProperty(name = "hack4edu.direccion.centro", defaultValue = "1")
    int centroDireccion;

    @ConfigProperty(name = "hack4edu.docente.centro", defaultValue = "1")
    int centroDocente;

    @ConfigProperty(name = "hack4edu.docente.grupo", defaultValue = "1")
    int grupoDocente;

    /** Permiso del usuario de la petición actual (el rol más amplio que tenga). */
    public Permiso permiso() {
        if (identidad.hasRole("administracion")) {
            return new Permiso("administracion", null, null);
        }
        if (identidad.hasRole("direccion")) {
            return new Permiso("direccion", centroDireccion, null);
        }
        if (identidad.hasRole("docente")) {
            return new Permiso("docente", centroDocente, grupoDocente);
        }
        throw ErrorAplicacion.prohibido("Tu usuario no tiene ningún rol asignado.");
    }

    /** Valida el ámbito pedido contra el del usuario. Lanza 403 si se sale de él. */
    public Ambito comprobar(Integer centro, Integer grupo) {
        Ambito a = Ambito.de(centro, grupo);
        Permiso p = permiso();
        switch (p.rol()) {
            case "administracion" -> {
                if (a.grupo() != null) {
                    throw ErrorAplicacion.prohibido("Administración consulta centros, no grupos.");
                }
            }
            case "direccion" -> {
                if (a.centro() == null || a.centro() != p.centro().intValue()) {
                    throw ErrorAplicacion.prohibido("Solo puedes consultar tu centro.");
                }
                if (a.grupo() != null && !grupoDelCentro(a.grupo(), a.centro())) {
                    throw ErrorAplicacion.prohibido("Ese grupo no es de tu centro.");
                }
            }
            default -> {
                if (a.centro() == null || a.grupo() == null
                        || a.centro() != p.centro().intValue() || a.grupo() != p.grupo().intValue()) {
                    throw ErrorAplicacion.prohibido("Solo puedes consultar tu grupo.");
                }
            }
        }
        return a;
    }

    /** Para lo que solo tiene sentido a nivel de conjunto de centros (comparativa entre centros). */
    public void exigirTodosLosCentros() {
        if (!permiso().todosLosCentros()) {
            throw ErrorAplicacion.prohibido("Solo administración puede comparar todos los centros.");
        }
    }

    private boolean grupoDelCentro(int grupo, int centro) {
        return !consultas.lista("SELECT 1 FROM analitica.grupo WHERE id = ? AND centro_id = ?",
                List.of(grupo, centro), rs -> 1).isEmpty();
    }
}
