package edu.escuelaing.tdse.networking.sockets;

import java.io.IOException;

/**
 * Etapa 2 - Ejercicio 4.3.1: servidor que recibe un numero y devuelve su cuadrado.
 *
 * <p>Uso: {@code java SquareServer [puerto]} (por defecto {@value #DEFAULT_PORT}).
 * Se prueba con {@link EchoClient} apuntando a ese puerto.
 */
public class SquareServer {

    public static final int DEFAULT_PORT = 35001;

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        LineServer server = new LineServer("SquareServer", SquareProtocol::new);
        try {
            server.start(port);
        } catch (IOException e) {
            System.err.println("No fue posible escuchar en el puerto " + port + ": "
                    + e.getMessage());
        }
    }
}
