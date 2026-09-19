package edu.escuelaing.tdse.networking.sockets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Pruebas de integracion sobre sockets reales, en un puerto efimero (0) de loopback.
 * Verifican que el servidor y el cliente hablan el mismo protocolo de texto.
 */
@Timeout(value = 15, unit = TimeUnit.SECONDS)
class LineServerTest {

    private LineServer server;
    private Thread serverThread;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (server != null) {
            server.stop();
        }
        if (serverThread != null) {
            serverThread.join(5000);
        }
    }

    /** Arranca el servidor en un hilo aparte y espera a que el puerto quede asignado. */
    private int startServer(Supplier<LineProtocol> protocol) throws Exception {
        server = new LineServer("TestServer", protocol);
        serverThread = new Thread(() -> {
            try {
                server.start(0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }, "test-line-server");
        serverThread.setDaemon(true);
        serverThread.start();

        while (server.getPort() <= 0) {
            Thread.sleep(10);
        }
        return server.getPort();
    }

    /** Abre una conexion, envia las lineas indicadas y devuelve las respuestas. */
    private List<String> exchange(int port, String... requests) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port);
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            List<String> responses = new java.util.ArrayList<>();
            for (String request : requests) {
                out.println(request);
                responses.add(in.readLine());
            }
            return responses;
        }
    }

    @Test
    void servidorDeCuadradosDeberiaResponderPorSocket() throws Exception {
        int port = startServer(SquareProtocol::new);

        assertEquals(List.of("25", "144", "6.25"), exchange(port, "5", "12", "2.5"));
    }

    @Test
    void servidorDeFuncionesDeberiaMantenerLaOperacionDuranteLaConexion() throws Exception {
        int port = startServer(MathFunctionProtocol::new);

        List<String> responses = exchange(port,
                "0",            // cos por defecto
                "pi/2",
                "fun:sin",
                "0",
                "pi/2");

        assertEquals("1", responses.get(0));
        assertEquals("0", responses.get(1));
        assertTrue(responses.get(2).contains("sin"));
        assertEquals("0", responses.get(3));
        assertEquals("1", responses.get(4));
    }

    @Test
    void deberiaAtenderVariasConexionesConsecutivas() throws Exception {
        int port = startServer(MathFunctionProtocol::new);

        // Primer cliente: cambia la operacion a seno y evalua.
        assertEquals(List.of("Operacion cambiada a sin.", "0"), exchange(port, "fun:sin", "0"));

        // Segundo cliente sobre el mismo servidor: la conexion anterior ya se cerro y el
        // estado no se comparte, asi que vuelve a regir el coseno por defecto.
        assertEquals(List.of("1"), exchange(port, "0"));

        // Tercer cliente: el servidor sigue vivo tras dos conexiones completas.
        assertEquals(List.of("-1"), exchange(port, "pi"));
    }

    @Test
    void deberiaCerrarLaConexionAlRecibirElMensajeDeSalida() throws Exception {
        int port = startServer(SquareProtocol::new);

        try (Socket socket = new Socket("127.0.0.1", port);
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            out.println(EchoServer.QUIT_MESSAGE);
            assertEquals("Response: " + EchoServer.QUIT_MESSAGE, in.readLine());
            // El servidor cierra: no hay mas datos que leer.
            assertEquals(null, in.readLine());
        }
    }
}
