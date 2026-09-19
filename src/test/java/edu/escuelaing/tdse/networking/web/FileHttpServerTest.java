package edu.escuelaing.tdse.networking.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Ejercicio 4.5.1: pruebas de integracion del servidor web sobre sockets reales.
 * Se habla HTTP a mano para comprobar exactamente lo que sale por el socket.
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class FileHttpServerTest {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 1, 2, 3, (byte) 0xFF };

    @TempDir
    Path root;

    private FileHttpServer server;
    private Thread serverThread;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        Files.writeString(root.resolve("index.html"), "<h1>Inicio</h1>", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("pagina2.html"), "<h1>Dos</h1>", StandardCharsets.UTF_8);
        Files.write(root.resolve("logo.png"), PNG_BYTES);

        server = new FileHttpServer(root);
        serverThread = new Thread(() -> {
            try {
                server.start(0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }, "test-http-server");
        serverThread.setDaemon(true);
        serverThread.start();

        while (server.getPort() <= 0) {
            Thread.sleep(10);
        }
        port = server.getPort();
    }

    @AfterEach
    void stopServer() throws InterruptedException {
        server.stop();
        serverThread.join(5000);
    }

    /** Respuesta cruda: linea de estado, encabezados y cuerpo en bytes. */
    private record RawResponse(String statusLine, List<String> headers, byte[] body) {

        String header(String name) {
            String prefix = name.toLowerCase() + ":";
            return headers.stream()
                    .filter(h -> h.toLowerCase().startsWith(prefix))
                    .map(h -> h.substring(prefix.length()).trim())
                    .findFirst()
                    .orElse(null);
        }

        String bodyAsText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /** Envia una peticion HTTP a mano y lee la respuesta completa. */
    private RawResponse request(String requestLine) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream rawOut = socket.getOutputStream();
            PrintWriter out = new PrintWriter(rawOut, false);
            out.print(requestLine + "\r\n"
                    + "Host: 127.0.0.1:" + port + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n");
            out.flush();

            byte[] all;
            try (InputStream in = socket.getInputStream()) {
                all = in.readAllBytes();
            }
            return parse(all);
        }
    }

    /** Separa encabezados y cuerpo por la primera secuencia CRLF CRLF. */
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

    @Test
    void deberiaServirLaPaginaDeInicio() throws Exception {
        RawResponse response = request("GET / HTTP/1.1");

        assertEquals("HTTP/1.1 200 OK", response.statusLine());
        assertEquals("text/html; charset=UTF-8", response.header("Content-Type"));
        assertEquals("<h1>Inicio</h1>", response.bodyAsText());
    }

    @Test
    void deberiaDeclararUnContentLengthCoherenteConElCuerpo() throws Exception {
        RawResponse response = request("GET /index.html HTTP/1.1");

        assertEquals(String.valueOf(response.body().length), response.header("Content-Length"));
    }

    /** El requisito central del ejercicio: varias peticiones sin reiniciar el servidor. */
    @Test
    void deberiaAtenderVariasPeticionesConsecutivas() throws Exception {
        assertEquals("<h1>Inicio</h1>", request("GET /index.html HTTP/1.1").bodyAsText());
        assertEquals("<h1>Dos</h1>", request("GET /pagina2.html HTTP/1.1").bodyAsText());
        assertEquals(404, statusOf(request("GET /no-existe.html HTTP/1.1")));
        // Tras un 404 el servidor debe seguir atendiendo con normalidad.
        assertEquals("<h1>Inicio</h1>", request("GET / HTTP/1.1").bodyAsText());
    }

    @Test
    void deberiaServirUnaImagenSinCorromperLosBytes() throws Exception {
        RawResponse response = request("GET /logo.png HTTP/1.1");

        assertEquals("HTTP/1.1 200 OK", response.statusLine());
        assertEquals("image/png", response.header("Content-Type"));
        assertArrayEquals(PNG_BYTES, response.body());
    }

    @Test
    void deberiaResponder404ConPaginaDeError() throws Exception {
        RawResponse response = request("GET /falta.html HTTP/1.1");

        assertEquals("HTTP/1.1 404 Not Found", response.statusLine());
        assertTrue(response.bodyAsText().contains("404"));
    }

    @Test
    void deberiaRechazarMetodosNoSoportados() throws Exception {
        assertEquals(405, statusOf(request("POST /index.html HTTP/1.1")));
    }

    @Test
    void deberiaResponderHeadSinCuerpo() throws Exception {
        RawResponse response = request("HEAD /index.html HTTP/1.1");

        assertEquals("HTTP/1.1 200 OK", response.statusLine());
        assertEquals("15", response.header("Content-Length"));
        assertEquals(0, response.body().length);
    }

    @Test
    void deberiaBloquearElAccesoFueraDelDirectorioPublico() throws Exception {
        Files.writeString(root.getParent().resolve("secreto.txt"), "no leer",
                StandardCharsets.UTF_8);

        assertEquals(403, statusOf(request("GET /../secreto.txt HTTP/1.1")));
    }

    private int statusOf(RawResponse response) {
        return Integer.parseInt(response.statusLine().split(" ")[1]);
    }
}
