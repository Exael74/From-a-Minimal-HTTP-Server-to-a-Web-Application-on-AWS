package edu.escuelaing.tdse.networking.sockets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Etapa 2 - Seccion 2.2 (Figura 4): servidor de eco.
 *
 * <p>Escucha en un puerto, acepta una conexion, imprime cada mensaje recibido y responde
 * con {@code "Response: " + mensaje}. Termina cuando el cliente envia {@code Bye.}.
 *
 * <p>Uso: {@code java EchoServer [puerto]} (por defecto {@value #DEFAULT_PORT}).
 */
public class EchoServer {

    public static final int DEFAULT_PORT = 35000;

    /** Mensaje que hace que el servidor cierre la conexion. */
    public static final String QUIT_MESSAGE = "Bye.";

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        try {
            new EchoServer().start(port);
        } catch (IOException e) {
            System.err.println("No fue posible escuchar en el puerto " + port + ": "
                    + e.getMessage());
        }
    }

    /** Atiende una unica conexion y devuelve el control cuando esta termina. */
    public void start(int port) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("EchoServer escuchando en el puerto " + port + "...");
            try (Socket clientSocket = serverSocket.accept()) {
                System.out.println("Cliente conectado desde " + clientSocket.getInetAddress());
                serve(clientSocket);
            }
        }
        System.out.println("EchoServer finalizado.");
    }

    /** Lee lineas del cliente y devuelve la respuesta de eco por cada una. */
    private void serve(Socket clientSocket) throws IOException {
        try (PrintWriter out = new PrintWriter(
                     new java.io.OutputStreamWriter(
                             clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))) {

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Message: " + inputLine);
                String outputLine = "Response: " + inputLine;
                out.println(outputLine);
                if (inputLine.equals(QUIT_MESSAGE)) {
                    break;
                }
            }
        }
    }
}
