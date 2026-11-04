package es.hack4edu;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reglas de la arquitectura hexagonal, comprobadas leyendo los import de cada fichero:
 *
 *   dominio         → Java puro. Sin frameworks y sin depender de aplicacion ni de infraestructura.
 *   aplicacion      → casos de uso y puertos. Sin HTTP, JDBC, LangChain4j ni infraestructura.
 *   infraestructura → adaptadores (web, persistencia, ollama…). Un módulo no usa la infraestructura de otro.
 */
class ArquitecturaHexagonalTest {

    private static final Path RAIZ = Path.of("src/main/java/es/hack4edu");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+);", Pattern.MULTILINE);

    /** Prefijos de frameworks e infraestructura que ni el dominio ni la aplicación pueden importar. */
    private static final List<String> TECNOLOGIA = List.of(
            "jakarta.ws", "jakarta.json", "javax.sql", "java.sql", "dev.langchain4j", "org.postgresql",
            "org.jboss", "io.smallrye", "io.quarkiverse", "io.quarkus.agroal", "io.quarkus.security",
            "io.quarkus.runtime", "org.hibernate");

    private record Fuente(Path ruta, String modulo, String capa, List<String> imports) {
    }

    @Test
    void elDominioEsJavaPuro() throws IOException {
        List<String> fallos = new ArrayList<>();
        for (Fuente f : fuentes("dominio")) {
            for (String i : f.imports()) {
                boolean framework = i.startsWith("jakarta.") || i.startsWith("io.quarkus") || i.startsWith("org.eclipse")
                        || TECNOLOGIA.stream().anyMatch(i::startsWith);
                boolean capaSuperior = i.contains(".aplicacion.") || i.contains(".infraestructura.");
                if (framework || capaSuperior) {
                    fallos.add(f.ruta() + " importa " + i);
                }
            }
        }
        assertTrue(fallos.isEmpty(), "El dominio no puede depender de frameworks ni de capas superiores:\n"
                + String.join("\n", fallos));
    }

    @Test
    void laAplicacionNoConoceLosAdaptadores() throws IOException {
        List<String> fallos = new ArrayList<>();
        for (Fuente f : fuentes("aplicacion")) {
            for (String i : f.imports()) {
                if (i.contains(".infraestructura.") || TECNOLOGIA.stream().anyMatch(i::startsWith)) {
                    fallos.add(f.ruta() + " importa " + i);
                }
            }
        }
        assertTrue(fallos.isEmpty(), "La aplicación solo puede depender del dominio y de sus puertos:\n"
                + String.join("\n", fallos));
    }

    @Test
    void unModuloNoUsaLaInfraestructuraDeOtro() throws IOException {
        List<String> fallos = new ArrayList<>();
        for (Fuente f : fuentes("infraestructura")) {
            for (String i : f.imports()) {
                Matcher m = Pattern.compile("^es\\.hack4edu\\.(\\w+)\\.infraestructura\\.").matcher(i);
                if (m.find() && !m.group(1).equals(f.modulo()) && !m.group(1).equals("compartido")) {
                    fallos.add(f.ruta() + " importa " + i);
                }
            }
        }
        assertTrue(fallos.isEmpty(), "Los módulos se comunican por sus casos de uso, no por sus adaptadores:\n"
                + String.join("\n", fallos));
    }

    @Test
    void cadaFicheroEstaEnUnaCapaConocida() throws IOException {
        List<String> fallos = new ArrayList<>();
        try (Stream<Path> todos = Files.walk(RAIZ)) {
            todos.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                String capa = RAIZ.relativize(p).getName(1).toString();
                if (!List.of("dominio", "aplicacion", "infraestructura").contains(capa)) {
                    fallos.add(p.toString());
                }
            });
        }
        assertTrue(fallos.isEmpty(), "Cada clase debe vivir en dominio, aplicacion o infraestructura:\n"
                + String.join("\n", fallos));
    }

    private static List<Fuente> fuentes(String capa) throws IOException {
        List<Fuente> res = new ArrayList<>();
        try (Stream<Path> todos = Files.walk(RAIZ)) {
            for (Path p : todos.filter(x -> x.toString().endsWith(".java")).toList()) {
                Path rel = RAIZ.relativize(p);
                if (rel.getNameCount() < 3 || !rel.getName(1).toString().equals(capa)) {
                    continue;
                }
                Matcher m = IMPORT.matcher(Files.readString(p));
                List<String> imports = new ArrayList<>();
                while (m.find()) {
                    imports.add(m.group(1));
                }
                res.add(new Fuente(p, rel.getName(0).toString(), capa, imports));
            }
        }
        return res;
    }
}
