package es.hack4edu.ingesta.aplicacion.puertos;

/** La base de datos ha rechazado una operación de carga; el mensaje es el motivo, sin envoltorio técnico. */
public class FalloRepositorioCargas extends RuntimeException {

    public FalloRepositorioCargas(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
