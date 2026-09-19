package edu.escuelaing.tdse.networking.urls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Prueba la descarga del "navegador" contra un servidor local efimero, para no depender
 * de conexion a Internet.
 */
class MiniBrowserTest {

    private static final String BODY =
            "<!doctype html><html><body>Hola taller</body></html>";

    private HttpServer server;
    private int port;

    @TempDir
    Path tempDir;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pagina.html", exchange -> {
            byte[] bytes = BODY.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void deberiaGuardarElCuerpoDeLaRespuestaEnElArchivoDestino() throws Exception {
        Path target = tempDir.resolve(MiniBrowser.OUTPUT_FILE);

        Path result = MiniBrowser.download("http://127.0.0.1:" + port + "/pagina.html", target);

        assertEquals(target, result);
        assertTrue(Files.exists(result));
        assertEquals(BODY, Files.readString(result, StandardCharsets.UTF_8));
    }

    @Test
    void deberiaNombrarElArchivoDeSalidaResultHtml() {
        assertEquals("result.html", MiniBrowser.OUTPUT_FILE);
    }
}
