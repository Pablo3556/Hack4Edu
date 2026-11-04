package es.hack4edu.ingesta.aplicacion.puertos;

import es.hack4edu.ingesta.dominio.CampoIngesta;

import java.util.List;
import java.util.Map;

/**
 * Puerto de salida: escrituras de la ingesta. El adaptador usa un rol con permisos mínimos
 * (solo insertar en el área de entrada y ejecutar los tres puntos de entrada de la carga).
 * Todos los métodos lanzan {@link FalloRepositorioCargas} si la base de datos rechaza la operación.
 */
public interface RepositorioCargas {

    /** Registra el resultado de la validación. Devuelve el id de la carga. */
    int abrirCarga(String usuario, String archivo, int grupo, int periodo, int filas, int validas, int errores,
                   String estado, String resultado);

    void actualizarCarga(int id, String estado, Integer validas, Integer errores, String resultado);

    /** Inserta los registros normalizados y los transforma, todo en una transacción. */
    void confirmar(int cargaId, List<Map<CampoIngesta, String>> registros);
}
