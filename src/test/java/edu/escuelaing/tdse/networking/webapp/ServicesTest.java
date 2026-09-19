package edu.escuelaing.tdse.networking.webapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.escuelaing.tdse.networking.web.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * Los cuatro servicios hardcodeados, mas el servicio lento que sirve de instrumento de medicion.
 *
 * <p>El reloj se inyecta para que la prueba de {@code /time} pueda comparar contra un instante
 * conocido en lugar de contra "ahora".
 */
class ServicesTest {

    private static final Instant INSTANTE = Instant.parse("2026-09-10T15:04:05Z");

    private final Services servicios =
            new Services(Clock.fixed(INSTANTE, ZoneId.of("America/Bogota")));

    private static HttpRequest peticion(String linea) {
        try {
            return HttpRequest.parse(linea);
        } catch (HttpRequest.MalformedRequestException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static String cuerpo(HttpResponse response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private static void assertEsJson(HttpResponse response) {
        assertEquals("application/json; charset=UTF-8", response.contentType());
        assertTrue(cuerpo(response).startsWith("{"), "El cuerpo deberia ser un objeto JSON.");
        assertTrue(cuerpo(response).endsWith("}"), "El objeto JSON deberia estar cerrado.");
    }

    // --- Saludo ---------------------------------------------------------------------------

    @Test
    void elSaludoDeberiaIncluirElNombreRecibido() {
        HttpResponse response = servicios.greeting(peticion("GET /hello?name=Ana HTTP/1.1"));

        assertEquals(200, response.status());
        assertEsJson(response);
        assertTrue(cuerpo(response).contains("\"name\":\"Ana\""));
        assertTrue(cuerpo(response).contains("Hola, Ana."));
    }

    @Test
    void elSaludoDeberiaRecortarLosEspaciosDelNombre() {
        HttpResponse response = servicios.greeting(peticion("GET /hello?name=%20%20Ana%20 HTTP/1.1"));

        assertTrue(cuerpo(response).contains("\"name\":\"Ana\""));
    }

    @Test
    void elSaludoDeberiaFallarConUn400SiFaltaElNombre() {
        HttpResponse response = servicios.greeting(peticion("GET /hello HTTP/1.1"));

        assertEquals(400, response.status());
        assertEsJson(response);
        assertTrue(cuerpo(response).contains("\"error\":\"missing_parameter\""));
    }

    @Test
    void elSaludoDeberiaFallarConUn400SiElNombreEstaVacio() {
        assertEquals(400, servicios.greeting(peticion("GET /hello?name= HTTP/1.1")).status());
        assertEquals(400, servicios.greeting(peticion("GET /hello?name=%20 HTTP/1.1")).status());
    }

    @Test
    void elSaludoDeberiaRechazarUnNombreDemasiadoLargo() {
        String largo = "a".repeat(Services.MAX_NAME_LENGTH + 1);

        HttpResponse response = servicios.greeting(peticion("GET /hello?name=" + largo + " HTTP/1.1"));

        assertEquals(400, response.status());
        assertTrue(cuerpo(response).contains("parameter_too_long"));
    }

    /** Un nombre con comillas no puede romper el documento JSON. */
    @Test
    void elSaludoDeberiaEscaparUnNombreHostil() {
        HttpResponse response = servicios.greeting(peticion("GET /hello?name=a%22b HTTP/1.1"));

        assertEquals(200, response.status());
        assertTrue(cuerpo(response).contains("\"name\":\"a\\\"b\""));
        assertFalse(cuerpo(response).contains("\"a\"b\""));
    }

    // --- Cuadrado -------------------------------------------------------------------------

    @Test
    void elCuadradoDeberiaDevolverEntradaYResultado() {
        HttpResponse response = servicios.square(peticion("GET /square?number=12 HTTP/1.1"));

        assertEquals(200, response.status());
        assertEsJson(response);
        assertEquals("{\"service\":\"square\",\"input\":12,\"result\":144}", cuerpo(response));
    }

    @Test
    void elCuadradoDeberiaAceptarDecimalesYNegativos() {
        assertTrue(cuerpo(servicios.square(peticion("GET /square?number=2.5 HTTP/1.1")))
                .contains("\"result\":6.25"));
        assertTrue(cuerpo(servicios.square(peticion("GET /square?number=-3 HTTP/1.1")))
                .contains("\"result\":9"));
    }

    @Test
    void elCuadradoDeberiaFallarConUn400SiFaltaElNumero() {
        HttpResponse response = servicios.square(peticion("GET /square HTTP/1.1"));

        assertEquals(400, response.status());
        assertTrue(cuerpo(response).contains("missing_parameter"));
    }

    @Test
    void elCuadradoDeberiaFallarConUn400SiNoEsUnNumero() {
        HttpResponse response = servicios.square(peticion("GET /square?number=hola HTTP/1.1"));

        assertEquals(400, response.status());
        assertEsJson(response);
        assertTrue(cuerpo(response).contains("invalid_number"));
        assertTrue(cuerpo(response).contains("'hola'"));
    }

    @Test
    void elCuadradoDeberiaRechazarUnResultadoFueraDeRango() {
        HttpResponse response = servicios.square(peticion("GET /square?number=1e200 HTTP/1.1"));

        assertEquals(400, response.status());
        assertTrue(cuerpo(response).contains("out_of_range"));
    }

    // --- Hora y salud ---------------------------------------------------------------------

    @Test
    void laHoraDeberiaVenirDelRelojDelServidor() {
        HttpResponse response = servicios.serverTime(peticion("GET /time HTTP/1.1"));

        assertEquals(200, response.status());
        assertEsJson(response);
        assertTrue(cuerpo(response).contains("\"epochMillis\":" + INSTANTE.toEpochMilli()));
        assertTrue(cuerpo(response).contains("\"zone\":\"America/Bogota\""));
        // 15:04:05 UTC son las 10:04:05 en Bogota: la hora se reporta en la zona del servidor.
        assertTrue(cuerpo(response).contains("2026-09-10T10:04:05"));
    }

    @Test
    void laSaludDeberiaResponderOk() {
        HttpResponse response = servicios.health(peticion("GET /health HTTP/1.1"));

        assertEquals(200, response.status());
        assertEsJson(response);
        assertTrue(cuerpo(response).contains("\"status\":\"ok\""));
        assertTrue(cuerpo(response).contains("\"uptimeSeconds\":0"));
    }

    // --- Servicio lento -------------------------------------------------------------------

    @Test
    void elServicioLentoDeberiaEsperarLoPedido() {
        long inicio = System.nanoTime();

        HttpResponse response = servicios.slow(peticion("GET /slow?seconds=0.3 HTTP/1.1"));

        long transcurrido = (System.nanoTime() - inicio) / 1_000_000;
        assertEquals(200, response.status());
        assertTrue(transcurrido >= 300, "Deberia haber esperado al menos 300 ms, espero "
                + transcurrido);
    }

    @Test
    void elServicioLentoDeberiaRechazarUnaEsperaFueraDeRango() {
        assertEquals(400, servicios.slow(peticion("GET /slow?seconds=99 HTTP/1.1")).status());
        assertEquals(400, servicios.slow(peticion("GET /slow?seconds=-1 HTTP/1.1")).status());
        assertEquals(400, servicios.slow(peticion("GET /slow?seconds=abc HTTP/1.1")).status());
    }

    // --- Sin estado -----------------------------------------------------------------------

    /** Dos llamadas seguidas con nombres distintos no pueden contaminarse entre si. */
    @Test
    void losServiciosNoDeberianGuardarEstadoEntrePeticiones() {
        String primera = cuerpo(servicios.greeting(peticion("GET /hello?name=Ana HTTP/1.1")));
        String segunda = cuerpo(servicios.greeting(peticion("GET /hello?name=Luis HTTP/1.1")));
        String tercera = cuerpo(servicios.greeting(peticion("GET /hello?name=Ana HTTP/1.1")));

        assertEquals(primera, tercera);
        assertFalse(segunda.contains("Ana"));
    }
}
