package es.hack4edu.ingesta.aplicacion.puertos;

import es.hack4edu.ingesta.dominio.LectorCsv;
import es.hack4edu.ingesta.dominio.Previsualizacion;
import es.hack4edu.ingesta.dominio.ValidadorIngesta;

import java.util.Optional;

/** Puerto de salida: vistas previas pendientes de confirmar. Nunca se persisten en disco. */
public interface AlmacenVistasPrevias {

    /** @throws IllegalStateException si hay demasiadas importaciones pendientes */
    Previsualizacion guardar(String usuario, int cargaId, String archivo, int grupoId, int periodoId, String ambito,
                             String periodo, LectorCsv.Archivo csv, ValidadorIngesta.Informe informe);

    /** La vista previa del token, solo si pertenece al usuario y no ha caducado. */
    Optional<Previsualizacion> obtener(String token, String usuario);

    /** Saca la vista previa (tras confirmar). */
    void quitar(Previsualizacion p);

    /** Saca la vista previa y deja constancia en la auditoría. */
    void cerrar(Previsualizacion p, String estado, String resultado);
}
