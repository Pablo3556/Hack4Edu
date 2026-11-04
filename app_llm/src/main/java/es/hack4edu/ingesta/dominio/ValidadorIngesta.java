package es.hack4edu.ingesta.dominio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapeo de columnas y validación de un CSV de docente, sin tocar la base de datos.
 * Produce la vista previa (recuentos, incidencias, muestra) y, si todo es correcto,
 * los registros ya normalizados que se insertarán en staging.spf_raw.
 */
public final class ValidadorIngesta {

    /** Máximo de incidencias detalladas que se devuelven (los recuentos siempre son completos). */
    static final int MAX_INCIDENCIAS = 200;
    static final int TAM_MUESTRA = 10;
    /** Umbral de publicación de agregados (analitica.k_min()). */
    static final int K_MIN = 10;

    public enum EstadoColumna { AUTO, MANUAL, SIN_ASIGNAR, IDENTIFICATIVA, SENSIBLE, DERIVADA }

    public enum TipoIncidencia { FORMATO, RANGO, OBLIGATORIO_VACIO, ESTRUCTURA }

    public record Incidencia(int linea, String columna, String campo, String valor, TipoIncidencia tipo,
                             String mensaje) {
    }

    public record Columna(int indice, String nombre, String campo, EstadoColumna estado, String nota) {
    }

    public record Resumen(int filasTotal, int filasValidas, int filasConVacios, int erroresFormato,
                          int fueraDeRango, int obligatoriosVacios, int filasConError,
                          Map<String, Integer> vaciosPorCampo) {
    }

    public record Muestra(List<String> campos, List<String> etiquetas, List<List<String>> filas) {
    }

    public record Informe(List<Columna> columnas, Resumen resumen, String resumenTexto,
                          List<String> problemasMapeo, List<String> bloqueos, List<String> avisos,
                          List<Incidencia> incidencias, int incidenciasOmitidas, Muestra muestra,
                          boolean puedeConfirmar,
                          /* registros válidos normalizados (no se envían al navegador) */
                          List<Map<CampoIngesta, String>> registros) {
    }

    // Columnas que nunca se importan
    private static final Set<String> IDENTIFICATIVAS_EXACTAS = Set.of(
            "id", "id alumno", "id alumna", "id estudiante", "alumno", "alumna", "estudiante", "nombre",
            "nombre completo", "nombre y apellidos", "apellidos", "apellido", "apellidos y nombre", "dni", "nie",
            "nif", "pasaporte", "email", "e mail", "correo", "correo electronico", "telefono", "movil", "nia",
            "expediente", "numero de expediente", "n expediente", "fecha de nacimiento", "fecha nacimiento",
            "direccion", "domicilio", "codigo postal", "cip", "nuss", "student id", "name", "first name",
            "last name", "phone", "address");
    private static final Set<String> IDENTIFICATIVAS_PREFIJO = Set.of(
            "nombre", "apellido", "dni", "nie", "nif", "email", "correo", "telefono", "movil", "direccion",
            "expediente", "fecha nac", "nia ");
    private static final Set<String> SENSIBLES = Set.of(
            "learning disabilities", "dificultad aprendizaje", "dificultades de aprendizaje",
            "dificultad de aprendizaje", "dificultades aprendizaje", "nee", "neae", "necesidades educativas",
            "necesidades educativas especiales", "discapacidad", "diagnostico", "salud");
    private static final Set<String> DERIVADAS = Set.of(
            "school type", "tipo de centro", "tipo centro", "titularidad", "centro", "grupo", "curso", "clase");

    private ValidadorIngesta() {
    }

    /** Estado y campo propuestos para cada columna del archivo. */
    public static List<Columna> proponerMapeo(List<String> cabecera) {
        List<Columna> columnas = new ArrayList<>();
        Set<CampoIngesta> usados = java.util.EnumSet.noneOf(CampoIngesta.class);
        for (int i = 0; i < cabecera.size(); i++) {
            String nombre = cabecera.get(i);
            Columna excluida = excluida(i, nombre);
            if (excluida != null) {
                columnas.add(excluida);
                continue;
            }
            var campo = CampoIngesta.detectar(nombre).filter(c -> !usados.contains(c));
            if (campo.isPresent()) {
                usados.add(campo.get());
                columnas.add(new Columna(i, nombre, campo.get().codigo(), EstadoColumna.AUTO,
                        "Detectada automáticamente"));
            } else {
                columnas.add(new Columna(i, nombre, null, EstadoColumna.SIN_ASIGNAR,
                        "Sin correspondencia: se ignorará salvo que la asignes"));
            }
        }
        return columnas;
    }

    /** Aplica el mapeo elegido por el docente (campo por índice de columna; null = ignorar). */
    public static List<Columna> aplicarMapeo(List<String> cabecera, List<Columna> anteriores, List<String> elegidos) {
        List<Columna> columnas = new ArrayList<>();
        for (int i = 0; i < cabecera.size(); i++) {
            String nombre = cabecera.get(i);
            Columna excluida = excluida(i, nombre);
            String elegido = i < elegidos.size() ? vacioANull(elegidos.get(i)) : null;
            if (excluida != null && excluida.estado() != EstadoColumna.DERIVADA) {
                // Las columnas identificativas o sensibles no se pueden asignar
                columnas.add(excluida);
                continue;
            }
            Columna antes = i < anteriores.size() ? anteriores.get(i) : null;
            if (elegido == null) {
                columnas.add(excluida != null ? excluida
                        : new Columna(i, nombre, null, EstadoColumna.SIN_ASIGNAR, "Ignorada"));
            } else {
                boolean igual = antes != null && elegido.equals(antes.campo());
                EstadoColumna estado = igual ? antes.estado() : EstadoColumna.MANUAL;
                columnas.add(new Columna(i, nombre, elegido, estado,
                        estado == EstadoColumna.AUTO ? "Detectada automáticamente" : "Asignada manualmente"));
            }
        }
        return columnas;
    }

    private static Columna excluida(int i, String nombre) {
        String k = CampoIngesta.clave(nombre);
        if (IDENTIFICATIVAS_EXACTAS.contains(k) || IDENTIFICATIVAS_PREFIJO.stream().anyMatch(k::startsWith)) {
            return new Columna(i, nombre, null, EstadoColumna.IDENTIFICATIVA,
                    "Parece un dato identificativo: no se importa ni se guarda");
        }
        if (SENSIBLES.contains(k)) {
            return new Columna(i, nombre, null, EstadoColumna.SENSIBLE,
                    "Categoría sensible: no se importa ni se guarda");
        }
        if (DERIVADAS.contains(k)) {
            return new Columna(i, nombre, null, EstadoColumna.DERIVADA,
                    "No hace falta: centro, grupo y titularidad se toman de lo que elijas al importar");
        }
        return null;
    }

    /** Valida el archivo con el mapeo dado. */
    public static Informe validar(LectorCsv.Archivo archivo, List<Columna> columnas) {
        List<String> problemasMapeo = problemasMapeo(columnas);
        Map<CampoIngesta, Integer> indice = new EnumMap<>(CampoIngesta.class);
        for (Columna c : columnas) {
            if (c.campo() != null) {
                CampoIngesta.porCodigo(c.campo()).ifPresent(campo -> indice.putIfAbsent(campo, c.indice()));
            }
        }
        // Orden estable de los campos asignados (el del esquema interno)
        List<CampoIngesta> asignados = Arrays.stream(CampoIngesta.values()).filter(indice::containsKey).toList();

        int filasValidas = 0, filasConVacios = 0, formato = 0, rango = 0, obligatorios = 0, filasConError = 0;
        Map<CampoIngesta, Integer> vacios = new EnumMap<>(CampoIngesta.class);
        List<Incidencia> incidencias = new ArrayList<>();
        int omitidas = 0;
        List<Map<CampoIngesta, String>> registros = new ArrayList<>();
        int nCabecera = archivo.cabecera().size();

        for (LectorCsv.Fila fila : archivo.filas()) {
            List<String> celdas = fila.celdas();
            List<Incidencia> deLaFila = new ArrayList<>();
            boolean conVacios = false;
            Map<CampoIngesta, String> registro = new EnumMap<>(CampoIngesta.class);

            if (celdas.size() != nCabecera) {
                formato++;
                deLaFila.add(new Incidencia(fila.linea(), null, null, null, TipoIncidencia.ESTRUCTURA,
                        "La fila tiene " + celdas.size() + " campos y la cabecera " + nCabecera
                                + ". Revisa separadores o comillas en esta línea."));
            } else {
                for (CampoIngesta campo : asignados) {
                    int col = indice.get(campo);
                    String valor = celdas.get(col);
                    String nombreColumna = archivo.cabecera().get(col);
                    if (CampoIngesta.vacio(valor)) {
                        if (campo.valorObligatorio()) {
                            obligatorios++;
                            deLaFila.add(new Incidencia(fila.linea(), nombreColumna, campo.codigo(), "",
                                    TipoIncidencia.OBLIGATORIO_VACIO,
                                    "Falta «" + campo.etiqueta() + "», que es obligatorio en cada registro."));
                        } else {
                            conVacios = true;
                            vacios.merge(campo, 1, Integer::sum);
                        }
                        continue;
                    }
                    try {
                        registro.put(campo, campo.normalizar(valor));
                    } catch (CampoIngesta.ValorNoValido e) {
                        boolean esRango = e.problema() == CampoIngesta.Problema.RANGO;
                        if (esRango) rango++; else formato++;
                        deLaFila.add(new Incidencia(fila.linea(), nombreColumna, campo.codigo(),
                                CampoIngesta.recortar(valor.strip()),
                                esRango ? TipoIncidencia.RANGO : TipoIncidencia.FORMATO,
                                campo.etiqueta() + ": " + e.getMessage() + "."));
                    }
                }
            }

            if (deLaFila.isEmpty()) {
                filasValidas++;
                registros.add(registro);
            } else {
                filasConError++;
                for (Incidencia inc : deLaFila) {
                    if (incidencias.size() < MAX_INCIDENCIAS) incidencias.add(inc); else omitidas++;
                }
            }
            if (conVacios) {
                filasConVacios++;
            }
        }

        Map<String, Integer> vaciosPorCampo = new LinkedHashMap<>();
        vacios.forEach((c, n) -> vaciosPorCampo.put(c.codigo(), n));
        Resumen resumen = new Resumen(archivo.filas().size(), filasValidas, filasConVacios, formato, rango,
                obligatorios, filasConError, vaciosPorCampo);

        List<String> bloqueos = new ArrayList<>(problemasMapeo);
        if (problemasMapeo.isEmpty()) {
            if (formato > 0) {
                bloqueos.add(formato + (formato == 1 ? " error" : " errores") + " de formato. Corrige los valores "
                        + "señalados en la tabla de incidencias (por ejemplo, texto donde se espera un número) "
                        + "o asigna la columna al campo correcto, y vuelve a subir el archivo.");
            }
            if (rango > 0) {
                bloqueos.add(rango + (rango == 1 ? " valor" : " valores") + " fuera de rango. Revisa que las notas "
                        + "y la asistencia estén entre 0 y 100 (no sobre 10) y que las horas sean plausibles.");
            }
            if (obligatorios > 0) {
                bloqueos.add(obligatorios + (obligatorios == 1 ? " registro sin" : " registros sin")
                        + " nota final. Completa la nota o elimina esas filas del archivo.");
            }
            if (filasValidas == 0 && bloqueos.isEmpty()) {
                bloqueos.add("No hay ningún registro válido que importar.");
            }
        }

        List<String> avisos = new ArrayList<>();
        if (filasValidas > 0 && filasValidas < K_MIN) {
            avisos.add("Solo hay " + filasValidas + " registros válidos. Por privacidad, no se mostrará ningún "
                    + "indicador de un grupo con menos de " + K_MIN + " registros.");
        }
        vacios.forEach((c, n) -> avisos.add(n + (n == 1 ? " registro" : " registros") + " con «" + c.etiqueta()
                + "» vacío: se importarán sin ese dato y no contarán en sus indicadores."));
        for (Columna c : columnas) {
            if (c.estado() == EstadoColumna.IDENTIFICATIVA || c.estado() == EstadoColumna.SENSIBLE) {
                avisos.add("La columna «" + c.nombre() + "» se ignora. " + c.nota() + ".");
            }
        }

        boolean puedeConfirmar = bloqueos.isEmpty() && filasValidas > 0;
        Muestra muestra = muestra(asignados, registros);
        return new Informe(List.copyOf(columnas), resumen, resumenTexto(resumen), List.copyOf(problemasMapeo),
                List.copyOf(bloqueos), List.copyOf(avisos), List.copyOf(incidencias), omitidas, muestra,
                puedeConfirmar, registros);
    }

    static List<String> problemasMapeo(List<Columna> columnas) {
        List<String> problemas = new ArrayList<>();
        Map<String, List<String>> porCampo = new LinkedHashMap<>();
        for (Columna c : columnas) {
            if (c.campo() == null) continue;
            if (CampoIngesta.porCodigo(c.campo()).isEmpty()) {
                problemas.add("La columna «" + c.nombre() + "» está asignada a un campo desconocido («" + c.campo() + "»).");
                continue;
            }
            porCampo.computeIfAbsent(c.campo(), k -> new ArrayList<>()).add("«" + c.nombre() + "»");
        }
        porCampo.forEach((campo, cols) -> {
            if (cols.size() > 1) {
                problemas.add("El campo «" + CampoIngesta.porCodigo(campo).orElseThrow().etiqueta()
                        + "» está asignado a varias columnas (" + String.join(", ", cols)
                        + "). Deja solo una.");
            }
        });
        for (CampoIngesta c : CampoIngesta.values()) {
            if (c.columnaObligatoria() && !porCampo.containsKey(c.codigo())) {
                problemas.add("Falta la columna obligatoria «" + c.etiqueta() + "». Asígnala en la tabla de "
                        + "correspondencias o añádela al archivo.");
            }
        }
        return problemas;
    }

    private static Muestra muestra(List<CampoIngesta> campos, List<Map<CampoIngesta, String>> registros) {
        List<List<String>> filas = registros.stream().limit(TAM_MUESTRA)
                .map(r -> campos.stream().map(c -> c.mostrar(r.get(c))).toList())
                .toList();
        return new Muestra(campos.stream().map(CampoIngesta::codigo).toList(),
                campos.stream().map(CampoIngesta::etiqueta).toList(), filas);
    }

    /** "147 filas detectadas, 3 registros con asistencia vacía, 0 errores de formato". */
    static String resumenTexto(Resumen r) {
        List<String> partes = new ArrayList<>();
        partes.add(r.filasTotal() + (r.filasTotal() == 1 ? " fila detectada" : " filas detectadas"));
        partes.add(r.filasValidas() + (r.filasValidas() == 1 ? " registro válido" : " registros válidos"));
        r.vaciosPorCampo().forEach((codigo, n) -> partes.add(n + (n == 1 ? " registro" : " registros") + " con "
                + CampoIngesta.porCodigo(codigo).map(c -> c.etiqueta().toLowerCase().replace(" (%)", "")).orElse(codigo)
                + " " + vacio(codigo)));
        partes.add(r.erroresFormato() + (r.erroresFormato() == 1 ? " error" : " errores") + " de formato");
        partes.add(r.fueraDeRango() + (r.fueraDeRango() == 1 ? " valor" : " valores") + " fuera de rango");
        if (r.obligatoriosVacios() > 0) {
            partes.add(r.obligatoriosVacios() + " sin nota final");
        }
        return String.join(", ", partes);
    }

    /** Concordancia de «vacío» con el nombre del campo («asistencia vacía», «tutorías vacías»). */
    private static String vacio(String codigo) {
        return switch (codigo) {
            case "horas_estudio", "horas_sueno", "tutorias" -> "vacías";
            case "extraescolares", "estudios_familia" -> "vacíos";
            case "nota_examen", "asistencia", "nota_previa", "implicacion_familiar", "motivacion", "renta_familiar",
                 "calidad_docente", "actividad_fisica", "influencia_pares", "distancia_casa" -> "vacía";
            default -> "vacío";
        };
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** Lista de campos con su metadato, para la interfaz. */
    public static List<Map<String, Object>> esquema() {
        return Arrays.stream(CampoIngesta.values()).map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigo", c.codigo());
            m.put("etiqueta", c.etiqueta());
            m.put("descripcion", c.descripcion());
            m.put("obligatorio", c.columnaObligatoria());
            m.put("valorObligatorio", c.valorObligatorio());
            m.put("tipo", c.tipo().name().toLowerCase());
            if (c.tipo() == CampoIngesta.Tipo.ENTERO) {
                m.put("min", c.min());
                m.put("max", c.max());
            }
            m.put("valores", c.valoresAdmitidos());
            return m;
        }).collect(Collectors.toList());
    }
}
