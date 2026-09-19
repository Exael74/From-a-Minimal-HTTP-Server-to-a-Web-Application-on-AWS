package edu.escuelaing.tdse.networking.webapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Interpretacion de la linea de peticion y de la query string. */
class HttpRequestTest {

    @Test
    void deberiaSepararMetodoRutaYVersion() throws Exception {
        HttpRequest request = HttpRequest.parse("GET /index.html HTTP/1.1");

        assertEquals("GET", request.method());
        assertEquals("/index.html", request.path());
        assertEquals("HTTP/1.1", request.version());
        assertTrue(request.query().isEmpty());
    }

    @Test
    void deberiaDejarLaQueryFueraDeLaRuta() throws Exception {
        HttpRequest request = HttpRequest.parse("GET /square?number=5 HTTP/1.1");

        assertEquals("/square", request.path());
        assertEquals("5", request.queryParam("number"));
    }

    @Test
    void deberiaDecodificarLosParametros() throws Exception {
        HttpRequest request = HttpRequest.parse("GET /hello?name=Ana%20Mar%C3%ADa HTTP/1.1");

        // %C3%AD son los dos bytes UTF-8 de la i acentuada.
        assertEquals("Ana Mar" + (char) 0x00ED + "a", request.queryParam("name"));
    }

    /** En una query, '+' significa espacio: es la regla de los formularios. */
    @Test
    void deberiaTratarElMasComoEspacioEnLaQuery() throws Exception {
        HttpRequest request = HttpRequest.parse("GET /hello?name=Ana+Maria HTTP/1.1");

        assertEquals("Ana Maria", request.queryParam("name"));
    }

    @Test
    void deberiaLeerVariosParametros() throws Exception {
        HttpRequest request = HttpRequest.parse("GET /x?a=1&b=2&c= HTTP/1.1");

        assertEquals("1", request.queryParam("a"));
        assertEquals("2", request.queryParam("b"));
        assertEquals("", request.queryParam("c"));
        assertNull(request.queryParam("d"));
    }

    @Test
    void deberiaIgnorarElFragmento() throws Exception {
        HttpRequest request = HttpRequest.parse("GET /index.html?a=1#seccion HTTP/1.1");

        assertEquals("/index.html", request.path());
        assertEquals("1", request.queryParam("a"));
    }

    @Test
    void deberiaNormalizarElMetodoAMayusculas() throws Exception {
        assertEquals("GET", HttpRequest.parse("get / HTTP/1.1").method());
    }

    @Test
    void deberiaRechazarUnaLineaSinObjetivo() {
        assertThrows(HttpRequest.MalformedRequestException.class, () -> HttpRequest.parse("GET"));
    }

    @Test
    void deberiaRechazarUnaLineaVacia() {
        assertThrows(HttpRequest.MalformedRequestException.class, () -> HttpRequest.parse("   "));
    }

    @Test
    void deberiaReconocerLosMetodosDeLectura() throws Exception {
        assertTrue(HttpRequest.parse("GET / HTTP/1.1").isReadMethod());
        assertTrue(HttpRequest.parse("HEAD / HTTP/1.1").isReadMethod());
        assertFalse(HttpRequest.parse("POST / HTTP/1.1").isReadMethod());
    }
}
