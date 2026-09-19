package edu.escuelaing.tdse.networking.sockets;

import java.io.IOException;

/**
 * Etapa 2 - Ejercicio 4.3.2: servidor que aplica seno, coseno o tangente al numero recibido.
 *
 * <p>El comando {@code fun:sin}, {@code fun:cos} o {@code fun:tan} cambia la operacion activa;
 * la operacion por defecto es el coseno.
 *
 * <p>Uso: {@code java MathFunctionServer [puerto]} (por defecto {@value #DEFAULT_PORT}).
 * Se prueba con {@link EchoClient} apuntando a ese puerto.
 */
public class MathFunctionServer {

    public static final int DEFAULT_PORT = 35002;

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        LineServer server = new LineServer("MathFunctionServer", MathFunctionProtocol::new);
        try {
            server.start(port);
        } catch (IOException e) {
            System.err.println("No fue posible escuchar en el puerto " + port + ": "
                    + e.getMessage());
        }
    }
}
