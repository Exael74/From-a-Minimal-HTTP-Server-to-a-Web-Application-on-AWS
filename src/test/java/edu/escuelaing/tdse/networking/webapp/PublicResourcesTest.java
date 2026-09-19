package edu.escuelaing.tdse.networking.webapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.escuelaing.tdse.networking.web.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Resolucion de recursos estaticos.
 *
 * <p>Se usan dos raices: {@code publico-de-prueba} (en {@code src/test/resources}) para los casos
 * limite, y la raiz real {@code public} para confirmar que los recursos de la aplicacion quedaron
 * empaquetados donde se espera.
 */
class PublicResourcesTest {

    private final PublicResources recursos = new PublicResources("publico-de-prueba");
    private final PublicResources reales = new PublicResources();

    private static String texto(HttpResponse response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    @Test
    void deberiaServirElIndiceEnLaRaiz() {
        HttpResponse response = recursos.resolve("/");

        assertEquals(200, response.status());
        assertEquals("text/html; charset=UTF-8", response.contentType());
        assertEquals("<h1>Inicio de prueba</h1>", texto(response));
    }

    @Test
    void deberiaServirUnArchivoPorSuNombre() {
        HttpResponse response = recursos.resolve("/app.js");

        assertEquals(200, response.status());
        assertEquals("text/javascript; charset=UTF-8", response.contentType());
    }

    @Test
    void deberiaServirElIndiceDeUnSubdirectorio() {
        HttpResponse response = recursos.resolve("/sub");

        assertEquals(200, response.status());
        assertEquals("<h1>Indice del subdirectorio</h1>", texto(response));
    }

    @Test
    void deberiaDecodificarLosEscapesDeLaRuta() {
        HttpResponse response = recursos.resolve("/archivo%20con%20espacios.html");

        assertEquals(200, response.status());
        assertEquals("<h1>Con espacios</h1>", texto(response));
    }

    @Test
    void deberiaResponder404SiElArchivoNoExiste() {
        assertEquals(404, recursos.resolve("/no-existe.html").status());
    }

    /** Sin un Content-Type que anunciar, el recurso no se publica. */
    @Test
    void deberiaResponder404ConUnaExtensionDesconocida() {
        assertEquals(404, recursos.resolve("/notas.bin").status());
    }

    @Test
    void deberiaBloquearElRecorridoDeRutas() {
        HttpResponse response = recursos.resolve("/../secreto.txt");

        assertEquals(403, response.status());
        assertFalse(texto(response).contains("contenido privado"));
    }

    /** El caso real: el navegador no normaliza los puntos si vienen escapados. */
    @Test
    void deberiaBloquearElRecorridoDeRutasEscapado() {
        assertEquals(403, recursos.resolve("/%2e%2e/secreto.txt").status());
    }

    @Test
    void deberiaBloquearElRecorridoProfundo() {
        assertEquals(403, recursos.resolve("/sub/../../secreto.txt").status());
    }

    /** Subir y volver a bajar dentro de la raiz es legitimo y debe seguir funcionando. */
    @Test
    void deberiaPermitirUnRecorridoQueNoSaleDeLaRaiz() {
        assertEquals(200, recursos.resolve("/sub/../index.html").status());
    }

    @Test
    void deberiaRechazarLaBarraInvertida() {
        assertEquals(400, recursos.resolve("/..\\secreto.txt").status());
    }

    @Test
    void deberiaRechazarUnEscapeIncompleto() {
        assertEquals(400, recursos.resolve("/index%2.html").status());
    }

    @Test
    void deberiaNormalizarBarrasRepetidasYPuntoSimple() throws Exception {
        assertEquals("index.html", PublicResources.normalize("//./index.html"));
        assertEquals("sub/index.html", PublicResources.normalize("/sub//index.html"));
    }

    // --- Recursos reales de la aplicacion -------------------------------------------------

    @Test
    void deberiaEmpaquetarLaPaginaDeInicioDeLaAplicacion() {
        HttpResponse response = reales.resolve("/");

        assertEquals(200, response.status());
        assertEquals("text/html; charset=UTF-8", response.contentType());
        assertTrue(texto(response).contains("<script src=\"/app.js\"></script>"));
    }

    @Test
    void deberiaEmpaquetarElScriptYLaHojaDeEstilos() {
        assertEquals("text/javascript; charset=UTF-8", reales.resolve("/app.js").contentType());
        assertEquals("text/css; charset=UTF-8", reales.resolve("/styles.css").contentType());
    }

    /** Las imagenes se entregan como bytes: se comprueba su firma binaria, no su texto. */
    @Test
    void deberiaEmpaquetarLasDosImagenesConSuTipoCorrecto() {
        HttpResponse png = reales.resolve("/logo.png");
        assertEquals("image/png", png.contentType());
        assertEquals((byte) 0x89, png.body()[0]);
        assertEquals('P', png.body()[1]);

        HttpResponse jpeg = reales.resolve("/secuencial.jpg");
        assertEquals("image/jpeg", jpeg.contentType());
        assertEquals((byte) 0xFF, jpeg.body()[0]);
        assertEquals((byte) 0xD8, jpeg.body()[1]);
    }
}
