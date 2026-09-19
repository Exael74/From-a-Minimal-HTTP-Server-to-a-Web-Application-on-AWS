package edu.escuelaing.tdse.networking.sockets;

/**
 * Protocolo de texto linea a linea: por cada linea recibida se produce una linea de respuesta.
 *
 * <p>Separa la logica del ejercicio del manejo de sockets, de modo que puede probarse sin red.
 * Una instancia atiende una sola conexion, asi que puede guardar estado (por ejemplo, la
 * operacion activa en {@link MathFunctionProtocol}).
 */
@FunctionalInterface
public interface LineProtocol {

    /**
     * Procesa una linea recibida del cliente.
     *
     * @param request linea enviada por el cliente, sin el salto de linea
     * @return la linea de respuesta que debe enviarse
     */
    String process(String request);

    /**
     * Indica si la conexion debe cerrarse despues de responder a {@code request}.
     *
     * @return {@code true} si el cliente pidio terminar
     */
    default boolean isQuit(String request) {
        return EchoServer.QUIT_MESSAGE.equals(request);
    }
}
