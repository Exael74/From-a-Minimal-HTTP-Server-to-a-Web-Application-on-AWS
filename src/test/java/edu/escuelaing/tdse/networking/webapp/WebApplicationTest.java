package edu.escuelaing.tdse.networking.webapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Pruebas de integracion de la aplicacion completa sobre sockets reales.
 *
 * <p>Se habla HTTP a mano, sin cliente HTTP de la biblioteca estandar, para poder comprobar
 * exactamente los bytes que salen del servidor: linea de estado, encabezados y cuerpo.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class WebApplicationTest {

    private WebApplication application;
    private Thread serverThread;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        application = new WebApplication();
        serverThread = new Thread(() -> {
            try {
                application.start(0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }, "test-webapp");
        serverThread.setDaemon(true);
        serverThread.start();

        while (application.getPort() <= 0) {
            Thread.sleep(10);
        }
        port = application.getPort();
    }

    @AfterEach
    void stopServer() throws InterruptedException {
        application.stop();
        serverThread.join(5000);
    }

    /** Respuesta cruda: linea de estado, encabezados y cuerpo en bytes. */
    private record RawResponse(String statusLine, List<String> headers, byte[] body) {

        int status() {
            return Integer.parseInt(statusLine.split(" ")[1]);
        }

        String header(String name) {
            String prefix = name.toLowerCase() + ":";
            return headers.stream()
                    .filter(h -> h.toLowerCase().startsWith(prefix))
                    .map(h -> h.substring(prefix.length()).trim())
                    .findFirst()
                    .orElse(null);
        }

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private RawResponse get(String target) throws IOException {
        return request("GET " + target + " HTTP/1.1");
    }

    /** Envia una peticion HTTP a mano y lee la respuesta completa. */
    private RawResponse request(String requestLine) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream out = socket.getOutputStream();
            out.write((requestLine + "\r\n"
                    + "Host: 127.0.0.1:" + port + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();

            byte[] all;
            try (InputStream in = socket.getInputStream()) {
                all = in.readAllBytes();
            }
            return parse(all);
        }
    }

    private RawResponse parse(byte[] all) {
        int separator = indexOfBlankLine(all);
        String head = new String(all, 0, separator, StandardCharsets.US_ASCII);
        byte[] body = new byte[all.length - (separator + 4)];
        System.arraycopy(all, separator + 4, body, 0, body.length);

        List<String> lines = new ArrayList<>(List.of(head.split("\r\n")));
        String statusLine = lines.remove(0);
        return new RawResponse(statusLine, lines, body);
    }

    private int indexOfBlankLine(byte[] data) {
        for (int i = 0; i + 3 < data.length; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n'
                    && data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i;
            }
        }
        throw new IllegalStateException("La respuesta no tiene separador de encabezados.");
    }

    // --- Recursos estaticos ---------------------------------------------------------------

    @Test
    void deberiaServirLaPaginaDeInicio() throws Exception {
        RawResponse response = get("/");

        assertEquals("HTTP/1.1 200 OK", response.statusLine());
        assertEquals("text/html; charset=UTF-8", response.header("Content-Type"));
        assertTrue(response.text().contains("<title>"));
    }

    @Test
    void deberiaDeclararUnContentLengthIgualALosBytesEnviados() throws Exception {
        RawResponse response = get("/app.js");

        assertEquals(String.valueOf(response.body().length), response.header("Content-Length"));
    }

    /**
     * Las imagenes se comparan por su firma binaria. Si el servidor las tratara como texto, el
     * primer byte 0x89 del PNG se habria convertido en el caracter de reemplazo de UTF-8.
     */
    @Test
    void deberiaServirLasImagenesSinCorromperLosBytes() throws Exception {
        RawResponse png = get("/logo.png");
        assertEquals("image/png", png.header("Content-Type"));
        assertEquals((byte) 0x89, png.body()[0]);
        assertEquals(Integer.parseInt(png.header("Content-Length")), png.body().length);

        RawResponse jpeg = get("/secuencial.jpg");
        assertEquals("image/jpeg", jpeg.header("Content-Type"));
        assertEquals((byte) 0xFF, jpeg.body()[0]);
        assertEquals((byte) 0xD8, jpeg.body()[1]);
    }

    @Test
    void deberiaResponderHeadSinCuerpo() throws Exception {
        RawResponse response = request("HEAD /index.html HTTP/1.1");

        assertEquals(200, response.status());
        assertTrue(Integer.parseInt(response.header("Content-Length")) > 0);
        assertEquals(0, response.body().length);
    }

    // --- Servicios ------------------------------------------------------------------------

    @Test
    void deberiaResponderElSaludoEnJson() throws Exception {
        RawResponse response = get("/hello?name=Ana");

        assertEquals(200, response.status());
        assertEquals("application/json; charset=UTF-8", response.header("Content-Type"));
        assertTrue(response.text().contains("\"name\":\"Ana\""));
    }

    @Test
    void deberiaResponderElCuadradoEnJson() throws Exception {
        assertTrue(get("/square?number=12").text().contains("\"result\":144"));
    }

    @Test
    void deberiaResponderLaHoraYLaSalud() throws Exception {
        assertTrue(get("/time").text().contains("\"service\":\"server-time\""));
        assertTrue(get("/health").text().contains("\"status\":\"ok\""));
    }

    // --- Errores controlados --------------------------------------------------------------

    @Test
    void deberiaResponder404AUnArchivoInexistente() throws Exception {
        assertEquals(404, get("/no-existe.html").status());
    }

    @Test
    void deberiaResponder400AUnServicioSinSuParametro() throws Exception {
        RawResponse response = get("/hello");

        assertEquals(400, response.status());
        assertTrue(response.text().contains("missing_parameter"));
    }

    @Test
    void deberiaResponder405AUnMetodoNoSoportado() throws Exception {
        assertEquals(405, request("POST /index.html HTTP/1.1").status());
    }

    @Test
    void deberiaResponder403AUnIntentoDeRecorridoDeRutas() throws Exception {
        RawResponse response = get("/%2e%2e/pom.xml");

        assertEquals(403, response.status());
        assertFalse(response.text().contains("<artifactId>"));
    }

    /**
     * La variante que si llega intacta desde un navegador: los navegadores resuelven los {@code ..}
     * antes de enviar la peticion, incluso escritos como {@code %2e%2e}, pero no decodifican el
     * {@code %2f}. Es la forma en que un cliente real puede intentar el recorrido.
     */
    @Test
    void deberiaBloquearElRecorridoConLaBarraEscapada() throws Exception {
        assertEquals(403, get("/..%2fpom.xml").status());
    }

    @Test
    void deberiaResponder400AUnaLineaDePeticionMalFormada() throws Exception {
        // Una sola palabra: no hay objetivo que interpretar.
        assertEquals(400, request("GET").status());
    }

    /** Una peticion invalida no puede dejar el servidor inutilizable para la siguiente. */
    @Test
    void deberiaSeguirAtendiendoDespuesDeUnaPeticionInvalida() throws Exception {
        assertEquals(400, request("BASURA").status());
        assertEquals(403, get("/%2e%2e/pom.xml").status());
        assertEquals(404, get("/no-existe.html").status());

        assertEquals(200, get("/health").status());
    }

    // --- Ciclo de vida secuencial ---------------------------------------------------------

    /** Requisito de la matriz de pruebas: 10 operaciones seguidas en una sola corrida. */
    @Test
    void deberiaAtenderDiezOperacionesConsecutivasEnUnaSolaCorrida() throws Exception {
        for (int i = 1; i <= 10; i++) {
            RawResponse response = get("/square?number=" + i);

            assertEquals(200, response.status(), "Fallo la operacion numero " + i);
            assertTrue(response.text().contains("\"result\":" + (i * i)));
        }
        assertEquals(200, get("/").status());
    }

    /**
     * Demostracion automatizada del limite secuencial.
     *
     * <p>Mientras {@code /slow} ocupa al unico hilo del servidor, una segunda conexion se conecta
     * de inmediato (la acepta la cola del sistema operativo) pero <b>no obtiene respuesta</b>
     * hasta que la primera termina. Es la misma espera que se ve en la pestana Network del
     * navegador con dos ventanas abiertas.
     */
    @Test
    void deberiaHacerEsperarALaSegundaPeticionMientrasAtiendeUnaLenta() throws Exception {
        Thread lenta = new Thread(() -> {
            try {
                get("/slow?seconds=1");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }, "peticion-lenta");
        lenta.setDaemon(true);
        lenta.start();

        // Margen para que la peticion lenta ya este siendo atendida.
        Thread.sleep(250);

        long inicio = System.nanoTime();
        RawResponse response = get("/health");
        long esperaMillis = (System.nanoTime() - inicio) / 1_000_000;

        assertEquals(200, response.status());
        assertTrue(esperaMillis >= 400,
                "La segunda peticion deberia haber esperado a la lenta; espero " + esperaMillis
                        + " ms. Si es casi cero, el servidor dejo de ser secuencial.");
        lenta.join(5000);
    }

    // --- Configuracion del puerto ---------------------------------------------------------

    @Test
    void deberiaTomarElPuertoDelPrimerArgumento() {
        assertEquals(8080, WebApplication.resolvePort(new String[] {"8080"}));
    }

    @Test
    void deberiaUsarElPuertoPorDefectoSinArgumentos() {
        // La maquina que corre las pruebas podria tener su propia configuracion de puerto.
        Assumptions.assumeTrue(System.getProperty(WebApplication.PORT_PROPERTY) == null
                && System.getenv(WebApplication.PORT_ENV) == null);

        assertEquals(WebApplication.DEFAULT_PORT, WebApplication.resolvePort(new String[0]));
    }

    @Test
    void deberiaRechazarUnPuertoInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> WebApplication.resolvePort(new String[] {"ochenta"}));
        assertThrows(IllegalArgumentException.class,
                () -> WebApplication.resolvePort(new String[] {"70000"}));
    }
}
