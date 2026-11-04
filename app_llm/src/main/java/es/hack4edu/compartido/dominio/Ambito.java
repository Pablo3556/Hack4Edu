package es.hack4edu.compartido.dominio;

/**
 * Ámbito de una consulta: sin centro = conjunto de centros; con centro = ese centro;
 * con centro y grupo = ese grupo.
 */
public record Ambito(Integer centro, Integer grupo) {

    public static final Ambito GLOBAL = new Ambito(null, null);

    public static Ambito de(Integer centro, Integer grupo) {
        if (grupo != null && centro == null) {
            throw ErrorAplicacion.peticionInvalida("Para elegir un grupo hay que indicar también el centro");
        }
        return new Ambito(centro, grupo);
    }

    /** "global", "centro" o "grupo": el valor de la columna ambito de las vistas agregadas. */
    public String tipo() {
        return grupo != null ? "grupo" : centro != null ? "centro" : "global";
    }
}
