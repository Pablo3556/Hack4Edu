package es.hack4edu.datos.aplicacion.puertos;

import es.hack4edu.compartido.dominio.Ambito;
import es.hack4edu.datos.dominio.Agregados;

import java.util.List;
import java.util.Optional;

/** Puerto de salida: lectura de los datos agregados. Solo devuelve cifras de grupos que superan el umbral mínimo. */
public interface RepositorioAgregados {

    List<Agregados.Centro> ambitos();

    Optional<Agregados.Resumen> resumen(Ambito ambito);

    List<Agregados.Resumen> resumenDeCentros();

    /** Fila del centro y filas de sus grupos (los que superan el umbral). */
    List<Agregados.Resumen> resumenDelCentroYSusGrupos(int centro);

    List<Agregados.Prioridad> prioridades(Ambito ambito);

    List<Agregados.Brecha> brechas(Ambito ambito);

    List<Agregados.Factor> factores();

    List<Agregados.NivelFactor> nivelesDeFactor(String codigo, Ambito ambito);

    List<Agregados.Calidad> calidad();

    /** "Centro", o "Centro · Grupo"; vacío si no existe. */
    Optional<String> nombreAmbito(Ambito ambito);
}
