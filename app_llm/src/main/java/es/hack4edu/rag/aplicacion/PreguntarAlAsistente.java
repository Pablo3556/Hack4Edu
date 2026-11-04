package es.hack4edu.rag.aplicacion;

import es.hack4edu.compartido.dominio.ErrorAplicacion;
import es.hack4edu.rag.aplicacion.puertos.ModelosLenguaje;
import es.hack4edu.rag.dominio.Asistencia;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Caso de uso: preguntar al asistente de datos agregados, eligiendo el perfil de modelo. */
@ApplicationScoped
public class PreguntarAlAsistente {

    @Inject
    ModelosLenguaje modelos;

    public Asistencia.Respuesta preguntar(Asistencia.Pregunta p) {
        if (p == null || p.pregunta() == null || p.pregunta().isBlank()) {
            throw ErrorAplicacion.peticionInvalida("Falta el campo 'pregunta'");
        }
        String perfil = (p.modelo() == null || p.modelo().isBlank())
                ? Asistencia.MODELO_POR_DEFECTO : p.modelo().strip().toLowerCase();
        Asistencia.RespuestaModelo r = modelos.responder(perfil, p.pregunta().strip());
        return new Asistencia.Respuesta(r.contenido(), perfil, modelos.modeloConcreto(perfil), r.fuentes());
    }

    public Asistencia.Listado modelos() {
        return modelos.listar();
    }
}
