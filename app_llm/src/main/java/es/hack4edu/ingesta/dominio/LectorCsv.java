package es.hack4edu.ingesta.dominio;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lector de CSV tolerante con los formatos habituales de los centros:
 *   · separador "," (como el CSV de Kaggle), ";" (Excel en castellano) o tabulador
 *   · UTF-8 (con o sin BOM) o Windows-1252 (Excel antiguo)
 *   · comillas dobles (RFC 4180), con saltos de línea dentro de un campo
 * y estricto con lo que puede ser malicioso o un error: archivos binarios, demasiadas
 * filas o columnas, celdas enormes o comillas sin cerrar.
 */
public final class LectorCsv {

    public record Fila(int linea, List<String> celdas) {
    }

    public record Archivo(String codificacion, char separador, List<String> cabecera, List<Fila> filas) {
        public String separadorLegible() {
            return switch (separador) {
                case ';' -> "punto y coma";
                case '\t' -> "tabulador";
                default -> "coma";
            };
        }
    }

    /** Archivo que no se puede leer como CSV. El mensaje se muestra al docente. */
    public static final class CsvNoValido extends Exception {
        public CsvNoValido(String mensaje) {
            super(mensaje, null, false, false);
        }
    }

    public record Limites(int maxFilas, int maxColumnas, int maxLongitudCelda) {
    }

    private LectorCsv() {
    }

    public static Archivo leer(byte[] bytes, Limites limites) throws CsvNoValido {
        if (bytes.length == 0) {
            throw new CsvNoValido("El archivo está vacío.");
        }
        String codificacion = "UTF-8";
        String texto;
        try {
            texto = decodificar(bytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            codificacion = "Windows-1252";
            try {
                texto = decodificar(bytes, Charset.forName("windows-1252"));
            } catch (CharacterCodingException e2) {
                throw new CsvNoValido("No se reconoce la codificación del archivo. Guárdalo como «CSV UTF-8».");
            }
        }
        if (texto.startsWith("﻿")) {
            texto = texto.substring(1);
        }
        if (texto.indexOf('\0') >= 0) {
            throw new CsvNoValido("El archivo no parece un CSV de texto (¿es un Excel .xlsx?). "
                    + "Ábrelo en la hoja de cálculo y usa «Guardar como → CSV».");
        }

        char separador = detectarSeparador(texto);
        List<Fila> todas = analizar(texto, separador, limites);
        if (todas.isEmpty()) {
            throw new CsvNoValido("El archivo no tiene contenido.");
        }

        List<String> cabecera = new ArrayList<>();
        List<String> original = todas.get(0).celdas();
        for (int i = 0; i < original.size(); i++) {
            String nombre = original.get(i).strip();
            cabecera.add(nombre.isEmpty() ? "Columna " + (i + 1) : nombre);
        }
        if (cabecera.size() < 2) {
            throw new CsvNoValido("Solo se ha encontrado una columna. Comprueba que el separador sea coma, "
                    + "punto y coma o tabulador, y que la primera fila tenga los nombres de las columnas.");
        }
        if (cabecera.size() > limites.maxColumnas()) {
            throw new CsvNoValido("El archivo tiene " + cabecera.size() + " columnas; el máximo es "
                    + limites.maxColumnas() + ".");
        }
        List<Fila> filas = todas.subList(1, todas.size());
        if (filas.isEmpty()) {
            throw new CsvNoValido("El archivo solo tiene la fila de cabecera: no hay registros.");
        }
        return new Archivo(codificacion, separador, List.copyOf(cabecera), List.copyOf(filas));
    }

    private static String decodificar(byte[] bytes, Charset charset) throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    /** El separador más frecuente en la primera línea (fuera de comillas). */
    static char detectarSeparador(String texto) {
        int coma = 0, puntoComa = 0, tab = 0;
        boolean comillas = false;
        for (int i = 0; i < texto.length(); i++) {
            char ch = texto.charAt(i);
            if (ch == '"') {
                comillas = !comillas;
            } else if (!comillas) {
                if (ch == '\n' || ch == '\r') {
                    break;
                }
                if (ch == ',') coma++;
                else if (ch == ';') puntoComa++;
                else if (ch == '\t') tab++;
            }
        }
        if (puntoComa > coma && puntoComa >= tab) return ';';
        if (tab > coma && tab > puntoComa) return '\t';
        return ',';
    }

    private static List<Fila> analizar(String texto, char sep, Limites limites) throws CsvNoValido {
        List<Fila> filas = new ArrayList<>();
        List<String> actual = new ArrayList<>();
        StringBuilder celda = new StringBuilder();
        boolean comillas = false;
        int linea = 1;
        int lineaInicio = 1;
        int n = texto.length();

        for (int i = 0; i < n; i++) {
            char ch = texto.charAt(i);
            if (comillas) {
                if (ch == '"') {
                    if (i + 1 < n && texto.charAt(i + 1) == '"') {
                        celda.append('"');
                        i++;
                    } else {
                        comillas = false;
                    }
                } else {
                    if (ch == '\n') linea++;
                    celda.append(ch);
                }
            } else if (ch == '"' && celda.isEmpty()) {
                comillas = true;
            } else if (ch == sep) {
                actual.add(celda.toString());
                celda.setLength(0);
            } else if (ch == '\r' || ch == '\n') {
                if (ch == '\r' && i + 1 < n && texto.charAt(i + 1) == '\n') {
                    i++;
                }
                actual.add(celda.toString());
                celda.setLength(0);
                cerrarFila(filas, actual, lineaInicio, limites);
                actual = new ArrayList<>();
                linea++;
                lineaInicio = linea;
            } else {
                celda.append(ch);
            }
            if (celda.length() > limites.maxLongitudCelda()) {
                throw new CsvNoValido("La línea " + lineaInicio + " tiene un campo de más de "
                        + limites.maxLongitudCelda() + " caracteres. ¿Hay comillas sin cerrar?");
            }
        }
        if (comillas) {
            throw new CsvNoValido("Hay comillas sin cerrar a partir de la línea " + lineaInicio + ".");
        }
        if (!celda.isEmpty() || !actual.isEmpty()) {
            actual.add(celda.toString());
            cerrarFila(filas, actual, lineaInicio, limites);
        }
        return filas;
    }

    private static void cerrarFila(List<Fila> filas, List<String> celdas, int linea, Limites limites)
            throws CsvNoValido {
        if (celdas.stream().allMatch(c -> c.isBlank())) {
            return; // líneas en blanco (habituales al final del archivo)
        }
        if (celdas.size() > limites.maxColumnas() + 5) {
            throw new CsvNoValido("La línea " + linea + " tiene " + celdas.size() + " campos: revisa el separador.");
        }
        // filas.size() incluye la cabecera
        if (filas.size() > limites.maxFilas()) {
            throw new CsvNoValido("El archivo tiene más de " + limites.maxFilas()
                    + " registros. Divide el archivo por grupos e impórtalos por separado.");
        }
        filas.add(new Fila(linea, List.copyOf(celdas)));
    }
}
