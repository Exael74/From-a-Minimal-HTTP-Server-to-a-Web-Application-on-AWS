package edu.escuelaing.tdse.networking.web;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Etapa 2 - Ejercicio 4.5.1: servidor web que atiende varias peticiones <b>consecutivas</b>
 * (no concurrentes) y devuelve cualquier archivo solicitado, incluidos HTML e imagenes.
 *
 * <p>El bucle principal acepta una conexion, la atiende por completo, la cierra y vuelve a
 * esperar. No hay hilos: una segunda peticion espera a que termine la primera.
 *
 * <p>Uso: {@code java FileHttpServer [puerto] [directorioRaiz]}
 * (por defecto {@value #DEFAULT_PORT} y {@value #DEFAULT_ROOT}).
 */
public class FileHttpServer {

    public static final int DEFAULT_PORT = 35000;

    /** Directorio publico por defecto, relativo a la raiz del proyecto. */
    public static final String DEFAULT_ROOT = "www";

    private final StaticFileResolver resolver;

    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public FileHttpServer(Path root) {
        this.resolver = new StaticFileResolver(root);
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        Path root = Path.of(args.length > 1 ? args[1] : DEFAULT_ROOT);

        FileHttpServer server = new FileHttpServer(root);
        try {
            server.start(port);
        } catch (IOException e) {
            System.err.println("No fue posible escuchar en el puerto " + port + ": "
                    + e.getMessage());
        }
    }

    /**
     * Escucha en {@code port} y atiende peticiones una tras otra hasta {@link #stop()}.
     *
     * @param port puerto de escucha; 0 deja que el sistema asigne uno libre
     */
    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("Servidor web listo en http://localhost:" + getPort() + "/");
        System.out.println("Sirviendo archivos de: " + resolver.getRoot());

        while (running) {
            try (Socket clientSocket = serverSocket.accept()) {
                handle(clientSocket);
            } catch (SocketException e) {
                // Salida normal cuando stop() cierra el socket de escucha.
                if (running) {
                    throw e;
                }
            } catch (IOException e) {
                // Un error con un cliente no debe tumbar el servidor.
                System.err.println("Error atendiendo la peticion: " + e.getMessage());
            }
        }
        System.out.println("Servidor web detenido.");
    }

    /** Atiende una peticion completa sobre una conexion y la cierra al terminar. */
    private void handle(Socket clientSocket) throws IOException {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
        OutputStream out = new BufferedOutputStream(clientSocket.getOutputStream());

        String requestLine = in.readLine();
        if (requestLine == null || requestLine.isBlank()) {
            return; // Conexion abierta y cerrada sin enviar nada (el navegador lo hace a veces).
        }
        System.out.println("Received: " + requestLine);

        // Los encabezados se consumen hasta la linea en blanco que cierra la peticion.
        String header;
        while ((header = in.readLine()) != null && !header.isEmpty()) {
            System.out.println("  " + header);
        }

        HttpResponse response = respondTo(requestLine);
        System.out.println("--> " + response.status() + " " + response.reason()
                + " (" + response.contentType() + ", " + response.body().length + " bytes)");

        out.write(response.headerBytes());
        // HEAD pide los mismos encabezados que GET pero sin cuerpo.
        if (!requestLine.startsWith("HEAD ")) {
            out.write(response.body());
        }
        out.flush();
    }

    /**
     * Interpreta la linea de peticion y produce la respuesta.
     *
     * @param requestLine linea completa, p. ej. {@code GET /index.html HTTP/1.1}
     */
    HttpResponse respondTo(String requestLine) {
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            return HttpResponse.error(400, "Bad Request", "Linea de peticion malformada.");
        }
        String method = parts[0];
        String target = parts[1];

        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            return HttpResponse.error(405, "Method Not Allowed",
                    "Este servidor solo soporta GET y HEAD.");
        }
        return resolver.resolve(target);
    }

    /** Puerto real de escucha; util cuando se arranco con el puerto 0. */
    public int getPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? -1 : socket.getLocalPort();
    }

    /** Detiene el bucle de aceptacion y libera el puerto. */
    public void stop() {
        running = false;
        ServerSocket socket = serverSocket;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error al cerrar el servidor: " + e.getMessage());
            }
        }
    }
}
