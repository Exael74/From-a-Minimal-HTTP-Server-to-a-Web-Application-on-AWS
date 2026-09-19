package edu.escuelaing.tdse.networking.urls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import org.junit.jupiter.api.Test;

class URLInspectorTest {

    @Test
    void deberiaExtraerLosOchoComponentesDeUnaUrlCompleta() throws Exception {
        URL url = URLInspector.buildURL(
                "http://ldbn.escuelaing.edu.co:80/docs/index.html?lang=es&page=2#seccion3");
        Map<String, String> c = URLInspector.components(url);

        assertEquals("http", c.get("getProtocol"));
        assertEquals("ldbn.escuelaing.edu.co:80", c.get("getAuthority"));
        assertEquals("ldbn.escuelaing.edu.co", c.get("getHost"));
        assertEquals("80", c.get("getPort"));
        assertEquals("/docs/index.html", c.get("getPath"));
        assertEquals("lang=es&page=2", c.get("getQuery"));
        assertEquals("/docs/index.html?lang=es&page=2", c.get("getFile"));
        assertEquals("seccion3", c.get("getRef"));
    }

    @Test
    void deberiaReportarLosOchoMetodosEnOrden() throws Exception {
        Map<String, String> c = URLInspector.components(
                URLInspector.buildURL(URLInspector.DEFAULT_URL));

        assertEquals(8, c.size());
        assertEquals(
                java.util.List.of("getProtocol", "getAuthority", "getHost", "getPort",
                        "getPath", "getQuery", "getFile", "getRef"),
                java.util.List.copyOf(c.keySet()));
    }

    @Test
    void deberiaIndicarElPuertoPorDefectoCuandoNoEstaDeclarado() throws Exception {
        Map<String, String> c = URLInspector.components(
                URLInspector.buildURL("https://www.escuelaing.edu.co/index.html"));

        assertTrue(c.get("getPort").startsWith("-1"));
        assertTrue(c.get("getPort").contains("443"));
        assertEquals("(null)", c.get("getQuery"));
        assertEquals("(null)", c.get("getRef"));
    }

    @Test
    void deberiaMostrarRutaVaciaCuandoLaUrlNoTieneRuta() throws Exception {
        Map<String, String> c = URLInspector.components(
                URLInspector.buildURL("http://www.google.com"));

        assertEquals("(vacio)", c.get("getPath"));
    }

    @Test
    void deberiaFallarConUnaDireccionMalFormada() {
        assertThrows(URISyntaxException.class, () -> URLInspector.buildURL("http://no valido"));
    }
}
