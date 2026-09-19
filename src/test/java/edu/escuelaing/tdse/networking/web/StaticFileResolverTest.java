package edu.escuelaing.tdse.networking.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ejercicio 4.5.1: resolucion de la ruta pedida al archivo que debe servirse. */
class StaticFileResolverTest {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 1, 2, 3, (byte) 0xFF };

    @TempDir
    Path root;

    private StaticFileResolver resolver;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(root.resolve("index.html"), "<h1>Inicio</h1>", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("styles.css"), "body{color:red}", StandardCharsets.UTF_8);
        Files.createDirectories(root.resolve("img"));
        Files.write(root.resolve("img/logo.png"), PNG_BYTES);
        Files.writeString(root.resolve("con espacio.html"), "<p>ok</p>", StandardCharsets.UTF_8);

        resolver = new StaticFileResolver(root);
    }

    @Test
    void deberiaServirIndexHtmlEnLaRaiz() {
        HttpResponse response = resolver.resolve("/");

        assertEquals(200, response.status());
        assertEquals("text/html; charset=UTF-8", response.contentType());
        assertEquals("<h1>Inicio</h1>", new String(response.body(), StandardCharsets.UTF_8));
    }

    @Test
    void deberiaServirUnArchivoHtmlPorSuNombre() {
        HttpResponse response = resolver.resolve("/index.html");

        assertEquals(200, response.status());
        assertEquals("text/html; charset=UTF-8", response.contentType());
    }

    @Test
    void deberiaServirUnaHojaDeEstilosConSuTipoMime() {
        assertEquals("text/css; charset=UTF-8", resolver.resolve("/styles.css").contentType());
    }

    /** Una imagen debe llegar byte a byte y sin alteraciones. */
    @Test
    void deberiaServirUnaImagenBinariaIntacta() {
        HttpResponse response = resolver.resolve("/img/logo.png");

        assertEquals(200, response.status());
        assertEquals("image/png", response.contentType());
        assertArrayEquals(PNG_BYTES, response.body());
    }

    @Test
    void deberiaIgnorarLaQueryStringAlBuscarElArchivo() {
        assertEquals(200, resolver.resolve("/index.html?v=2&cache=no").status());
    }

    @Test
    void deberiaDecodificarLasRutasConCaracteresEscapados() {
        assertEquals(200, resolver.resolve("/con%20espacio.html").status());
    }

    @Test
    void deberiaResponder404CuandoElArchivoNoExiste() {
        HttpResponse response = resolver.resolve("/no-existe.html");

        assertEquals(404, response.status());
        assertEquals("Not Found", response.reason());
        assertTrue(new String(response.body(), StandardCharsets.UTF_8).contains("404"));
    }

    @Test
    void deberiaResponder404CuandoElDirectorioNoTieneIndex() throws IOException {
        Files.createDirectories(root.resolve("vacio"));

        assertEquals(404, resolver.resolve("/vacio").status());
    }

    /** Sin esta proteccion, el cliente podria leer archivos fuera del directorio publico. */
    @Test
    void deberiaBloquearRutasQueSalenDelDirectorioPublico() throws IOException {
        Files.writeString(root.getParent().resolve("secreto.txt"), "no leer",
                StandardCharsets.UTF_8);

        assertEquals(403, resolver.resolve("/../secreto.txt").status());
        assertEquals(403, resolver.resolve("/img/../../secreto.txt").status());
    }

    @Test
    void deberiaUsarTipoGenericoConExtensionDesconocida() throws IOException {
        Files.writeString(root.resolve("datos.xyz"), "contenido", StandardCharsets.UTF_8);

        assertEquals(MimeTypes.DEFAULT_TYPE, resolver.resolve("/datos.xyz").contentType());
    }
}
