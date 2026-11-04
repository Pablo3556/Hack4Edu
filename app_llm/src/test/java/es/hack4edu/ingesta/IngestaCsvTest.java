package es.hack4edu.ingesta;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Lector de CSV, mapeo de columnas y validación (sin Quarkus ni base de datos). */
class IngestaCsvTest {

    private static final LectorCsv.Limites LIMITES = new LectorCsv.Limites(5000, 60, 500);

    private static LectorCsv.Archivo leer(String texto, Charset cs) throws Exception {
        return LectorCsv.leer(texto.getBytes(cs), LIMITES);
    }

    private static ValidadorIngesta.Informe validar(String csv) throws Exception {
        LectorCsv.Archivo a = leer(csv, StandardCharsets.UTF_8);
        return ValidadorIngesta.validar(a, ValidadorIngesta.proponerMapeo(a.cabecera()));
    }

    private static String filas(int n, String fila) {
        return (fila + "\n").repeat(n);
    }

    @Test
    void detectaPuntoYComaWindows1252YComillas() throws Exception {
        LectorCsv.Archivo a = leer("Nota final;Asistencia (%);Observación\r\n70;\"85,5\";\"línea\nnueva\"\r\n\r\n",
                Charset.forName("windows-1252"));
        assertEquals(';', a.separador());
        assertEquals("Windows-1252", a.codificacion());
        assertEquals(List.of("Nota final", "Asistencia (%)", "Observación"), a.cabecera());
        assertEquals(1, a.filas().size());
        assertEquals("línea\nnueva", a.filas().get(0).celdas().get(2));
    }

    @Test
    void rechazaBinariosComillasSinCerrarYExcesoDeFilas() {
        assertThrows(LectorCsv.CsvNoValido.class, () -> LectorCsv.leer(new byte[]{'a', 0, ',', 'b'}, LIMITES));
        assertThrows(LectorCsv.CsvNoValido.class, () -> leer("a,b\n\"1,2\n", StandardCharsets.UTF_8));
        assertThrows(LectorCsv.CsvNoValido.class, () -> leer("solo_una_columna\n1\n", StandardCharsets.UTF_8));
        LectorCsv.CsvNoValido e = assertThrows(LectorCsv.CsvNoValido.class, () -> LectorCsv.leer(
                ("a,b\n" + filas(11, "1,2")).getBytes(StandardCharsets.UTF_8), new LectorCsv.Limites(10, 60, 500)));
        assertTrue(e.getMessage().contains("más de 10"));
    }

    @Test
    void mapeoAutomaticoEnCastellanoYExclusiones() {
        List<ValidadorIngesta.Columna> c = ValidadorIngesta.proponerMapeo(List.of(
                "Nombre y apellidos", "Nota final", "Asistencia (%)", "Apoyo familiar", "Learning_Disabilities",
                "School_Type", "Hours_Studied", "Columna rara"));
        assertEquals(ValidadorIngesta.EstadoColumna.IDENTIFICATIVA, c.get(0).estado());
        assertEquals("nota_examen", c.get(1).campo());
        assertEquals("asistencia", c.get(2).campo());
        assertEquals("implicacion_familiar", c.get(3).campo());
        assertEquals(ValidadorIngesta.EstadoColumna.SENSIBLE, c.get(4).estado());
        assertEquals(ValidadorIngesta.EstadoColumna.DERIVADA, c.get(5).estado());
        assertEquals("horas_estudio", c.get(6).campo());
        assertEquals(ValidadorIngesta.EstadoColumna.SIN_ASIGNAR, c.get(7).estado());
    }

    @Test
    void cuentaVaciosErroresYRangosYBloquea() throws Exception {
        String csv = "Nota final;Asistencia (%);Apoyo familiar\n"
                + filas(12, "70;90;Alto")
                + "71;;medio\n"          // asistencia vacía: aviso, no error
                + "sobresaliente;80;bajo\n" // formato
                + "72;112;bajo\n"        // rango
                + ";80;bajo\n"           // obligatorio vacío
                + "73;85;regular\n";     // valor categórico no válido
        ValidadorIngesta.Informe i = validar(csv);
        ValidadorIngesta.Resumen r = i.resumen();
        assertEquals(17, r.filasTotal());
        assertEquals(13, r.filasValidas());
        assertEquals(1, r.filasConVacios());
        assertEquals(2, r.erroresFormato());
        assertEquals(1, r.fueraDeRango());
        assertEquals(1, r.obligatoriosVacios());
        assertEquals(Map.of("asistencia", 1), r.vaciosPorCampo());
        assertFalse(i.puedeConfirmar());
        assertEquals(3, i.bloqueos().size());
        assertTrue(i.resumenTexto().startsWith("17 filas detectadas, 13 registros válidos, 1 registro con asistencia vacía"));
    }

    @Test
    void normalizaAlFormatoDelCsvOriginal() throws Exception {
        ValidadorIngesta.Informe i = validar("Nota final,Asistencia (%),Extraescolares,Género,Estudios de la familia\n"
                + filas(10, "\"67,6\",89%,Sí,Mujer,Universidad"));
        assertTrue(i.puedeConfirmar());
        Map<CampoIngesta, String> r = i.registros().get(0);
        assertEquals("68", r.get(CampoIngesta.NOTA_EXAMEN));
        assertEquals("89", r.get(CampoIngesta.ASISTENCIA));
        assertEquals("Yes", r.get(CampoIngesta.EXTRAESCOLARES));
        assertEquals("Female", r.get(CampoIngesta.GENERO));
        assertEquals("College", r.get(CampoIngesta.ESTUDIOS_FAMILIA));
        assertEquals(List.of("68", "89", "sí", "grado", "mujer"), i.muestra().filas().get(0));
    }

    @Test
    void mapeoManualDetectaDuplicadosYFaltaDeObligatorias() throws Exception {
        LectorCsv.Archivo a = leer("Nombre;Nota final;Asistencia;Horas\nAna;70;90;10\n", StandardCharsets.UTF_8);
        List<ValidadorIngesta.Columna> antes = ValidadorIngesta.proponerMapeo(a.cabecera());
        // El nombre no se puede asignar aunque se pida; nota asignada dos veces; asistencia ignorada
        List<String> elegidos = new ArrayList<>(Arrays.asList("nota_examen", "nota_examen", null, "nota_examen"));
        List<ValidadorIngesta.Columna> despues = ValidadorIngesta.aplicarMapeo(a.cabecera(), antes, elegidos);
        assertNull(despues.get(0).campo());
        assertEquals(ValidadorIngesta.EstadoColumna.MANUAL, despues.get(3).estado());
        ValidadorIngesta.Informe i = ValidadorIngesta.validar(a, despues);
        assertFalse(i.puedeConfirmar());
        assertTrue(i.problemasMapeo().stream().anyMatch(p -> p.contains("varias columnas")));
        assertTrue(i.problemasMapeo().stream().anyMatch(p -> p.contains("Falta la columna obligatoria «Asistencia (%)»")));
    }

    @Test
    void filaConCamposDeMasEsErrorDeEstructura() throws Exception {
        ValidadorIngesta.Informe i = validar("Nota final,Asistencia\n70,90\n70,90,extra\n");
        assertEquals(1, i.resumen().erroresFormato());
        assertEquals(ValidadorIngesta.TipoIncidencia.ESTRUCTURA, i.incidencias().get(0).tipo());
        assertEquals(3, i.incidencias().get(0).linea());
    }

    @Test
    void avisaSiHayMenosDeDiezRegistros() throws Exception {
        ValidadorIngesta.Informe i = validar("Nota final,Asistencia\n" + filas(5, "70,90"));
        assertTrue(i.puedeConfirmar());
        assertTrue(i.avisos().stream().anyMatch(a -> a.contains("menos de 10")));
    }

    @Test
    void nombreDeArchivoSinRutasNiControles() {
        assertEquals("notas.csv", IngestaResource.nombreSeguro("C:\\Users\\x\\notas.csv"));
        assertEquals("notas.csv", IngestaResource.nombreSeguro("../../etc/notas\u0000.csv".replace("\u0000", "")));
        assertEquals("sin_nombre.csv", IngestaResource.nombreSeguro("  "));
    }
}
