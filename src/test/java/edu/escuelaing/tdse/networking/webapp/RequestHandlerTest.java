package edu.escuelaing.tdse.networking.webapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.escuelaing.tdse.networking.web.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Reparto de peticiones entre los servicios hardcodeados y los recursos estaticos. */
class RequestHandlerTest {

    private final RequestHandler handler =
            new RequestHandler(new PublicResources("publico-de-prueba"), new Services());

    private HttpResponse responder(String linea) {
        try {
            return handler.handle(HttpRequest.parse(linea));
        } catch (HttpRequest.MalformedRequestException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static String cuerpo(HttpResponse response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    @Test
    void deberiaLlevarLaRaizAlIndice() {
        assertEquals("<h1>Inicio de prueba</h1>", cuerpo(responder("GET / HTTP/1.1")));
    }

    @Test
    void deberiaReconocerLosCuatroServicios() {
        assertTrue(cuerpo(responder("GET /hello?name=Ana HTTP/1.1")).contains("\"greeting\""));
        assertTrue(cuerpo(responder("GET /square?number=3 HTTP/1.1")).contains("\"result\":9"));
        assertTrue(cuerpo(responder("GET /time HTTP/1.1")).contains("\"service\":\"server-time\""));
        assertTrue(cuerpo(responder("GET /health HTTP/1.1")).contains("\"status\":\"ok\""));
    }

    @Test
    void deberiaIgnorarLaBarraFinalEnLosServicios() {
        assertEquals(200, responder("GET /health/ HTTP/1.1").status());
    }

    @Test
    void deberiaTratarUnaRutaDesconocidaComoRecursoEstatico() {
        assertEquals(404, responder("GET /saludo HTTP/1.1").status());
    }

    /** Los servicios responden solo a GET, como pide el enunciado. */
    @Test
    void deberiaRechazarConUn405UnMetodoDistintoDeGetEnUnServicio() {
        HttpResponse response = responder("POST /health HTTP/1.1");

        assertEquals(405, response.status());
        assertEquals("application/json; charset=UTF-8", response.contentType());
        assertTrue(cuerpo(response).contains("method_not_allowed"));
    }

    @Test
    void deberiaRechazarConUn405UnMetodoNoSoportadoEnUnRecursoEstatico() {
        HttpResponse response = responder("DELETE /index.html HTTP/1.1");

        assertEquals(405, response.status());
        assertEquals("text/html; charset=UTF-8", response.contentType());
    }

    /** HEAD sigue permitido sobre archivos: es el mismo GET sin cuerpo. */
    @Test
    void deberiaAceptarHeadSobreUnRecursoEstatico() {
        assertEquals(200, responder("HEAD /index.html HTTP/1.1").status());
    }

    @Test
    void deberiaResponderErroresDeServicioEnJsonYDeArchivosEnHtml() {
        assertEquals("application/json; charset=UTF-8",
                responder("GET /square?number=hola HTTP/1.1").contentType());
        assertEquals("text/html; charset=UTF-8",
                responder("GET /falta.html HTTP/1.1").contentType());
    }

    @Test
    void deberiaBloquearElRecorridoDeRutas() {
        assertEquals(403, responder("GET /%2e%2e/secreto.txt HTTP/1.1").status());
    }
}
