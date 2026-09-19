package edu.escuelaing.tdse.networking.sockets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * Servidor TCP de texto que atiende conexiones de forma secuencial (una a la vez, no concurrente)
 * y delega el tratamiento de cada linea en un {@link LineProtocol}.
 *
 * <p>Es la base comun de los ejercicios 4.3.1 y 4.3.2: lo unico que cambia entre ellos es el
 * protocolo. Se crea un {@code LineProtocol} nuevo por conexion, de modo que el estado de un
 * cliente no se filtra al siguiente.
 */
public class LineServer {

    private final String name;
    private final Supplier<LineProtocol> protocolFactory;

    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    /**
     * @param name nombre del servidor, usado solo en los mensajes de consola
     * @param protocolFactory crea el protocolo que atendera cada conexion
     */
    public LineServer(String name, Supplier<LineProtocol> protocolFactory) {
        this.name = name;
        this.protocolFactory = protocolFactory;
    }

    /**
     * Escucha en {@code port} y atiende conexiones hasta que se llame a {@link #stop()}.
     *
     * @param port puerto de escucha; usar 0 deja que el sistema asigne uno libre
     */
    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println(name + " escuchando en el puerto " + getPort() + "...");

        while (running) {
            try (Socket clientSocket = serverSocket.accept()) {
                System.out.println("Cliente conectado desde " + clientSocket.getInetAddress());
                serve(clientSocket, protocolFactory.get());
                System.out.println("Conexion cerrada. Esperando el siguiente cliente...");
            } catch (SocketException e) {
                // El socket se cerro desde stop(): es la salida normal del bucle.
                if (running) {
                    throw e;
                }
            }
        }
    }

    /** Atiende una conexion: lee lineas, responde y cierra cuando el protocolo lo indica. */
    private void serve(Socket clientSocket, LineProtocol protocol) throws IOException {
        try (PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(
                             clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))) {

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Message: " + inputLine);
                String response = protocol.process(inputLine);
                out.println(response);
                if (protocol.isQuit(inputLine)) {
                    break;
                }
            }
        }
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
                System.err.println("Error al cerrar " + name + ": " + e.getMessage());
            }
        }
    }
}
