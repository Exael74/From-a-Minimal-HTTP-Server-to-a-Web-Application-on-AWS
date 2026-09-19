package edu.escuelaing.tdse.networking.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Etapa 2 - Seccion 2.4 (Figura 5): servidor web que atiende una sola peticion.
 *
 * <p>Imprime la peticion recibida y responde una pagina HTML fija. Muestra que HTTP no es mas
 * que texto sobre un socket TCP: linea de peticion, encabezados, linea en blanco y cuerpo.
 *
 * <p>Uso: {@code java SingleRequestHttpServer [puerto]} (por defecto {@value #DEFAULT_PORT}).
 * Luego abrir {@code http://localhost:35000/} en el navegador.
 */
public class SingleRequestHttpServer {

    public static final int DEFAULT_PORT = 35000;

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Ready to receive... http://localhost:" + port + "/");

            try (Socket clientSocket = serverSocket.accept();
                 PrintWriter out = new PrintWriter(
                         new OutputStreamWriter(
                                 clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(
                                 clientSocket.getInputStream(), StandardCharsets.UTF_8))) {

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("Received: " + inputLine);
                    // Una linea vacia marca el fin de los encabezados de la peticion.
                    if (inputLine.isEmpty()) {
                        break;
                    }
                }

                String body = "<!doctype html><html><head>"
                        + "<meta charset=\"UTF-8\">"
                        + "<title>My Web Site</title></head>"
                        + "<body>My Web Site</body></html>";

                out.print("HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/html; charset=UTF-8\r\n"
                        + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                        + "Connection: close\r\n"
                        + "\r\n"
                        + body);
                out.flush();
            }
            System.out.println("Peticion atendida. Servidor finalizado.");
        } catch (IOException e) {
            System.err.println("No fue posible escuchar en el puerto " + port + ": "
                    + e.getMessage());
        }
    }
}
