package edu.escuelaing.tdse.networking.sockets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Etapa 2 - Seccion 2.1 (Figura 3): cliente de sockets.
 *
 * <p>Lee lineas del teclado, las envia al servidor e imprime la respuesta. Sirve para los tres
 * servidores de la etapa: {@link EchoServer}, {@link SquareServer} y {@link MathFunctionServer},
 * porque todos hablan el mismo protocolo de texto linea a linea.
 *
 * <p>Uso: {@code java EchoClient [host] [puerto]}
 * (por defecto {@value #DEFAULT_HOST}:{@value EchoServer#DEFAULT_PORT}).
 */
public class EchoClient {

    /** Direccion de loopback: identifica al equipo local. */
    public static final String DEFAULT_HOST = "127.0.0.1";

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : EchoServer.DEFAULT_PORT;

        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedReader keyboard = new BufferedReader(
                     new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            System.out.println("Conectado a " + host + ":" + port
                    + ". Escriba mensajes ('" + EchoServer.QUIT_MESSAGE + "' para salir).");

            String userInput;
            while ((userInput = keyboard.readLine()) != null) {
                out.println(userInput);
                String response = in.readLine();
                if (response == null) {
                    System.out.println("El servidor cerro la conexion.");
                    break;
                }
                System.out.println("echo: " + response);
                if (userInput.equals(EchoServer.QUIT_MESSAGE)) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error de comunicacion con " + host + ":" + port + " - "
                    + e.getMessage());
        }
    }
}
