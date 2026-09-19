package edu.escuelaing.tdse.networking.sockets;

/**
 * Etapa 2 - Ejercicio 4.3.1: recibe un numero y devuelve su cuadrado.
 *
 * <p>La logica esta separada del socket para poder probarla sin red.
 */
public class SquareProtocol implements LineProtocol {

    @Override
    public String process(String request) {
        String value = request == null ? "" : request.trim();

        if (value.isEmpty()) {
            return "Error: ingrese un numero.";
        }
        if (isQuit(value)) {
            return "Response: " + EchoServer.QUIT_MESSAGE;
        }
        try {
            double number = Double.parseDouble(value);
            return Numbers.format(number * number);
        } catch (NumberFormatException e) {
            return "Error: '" + value + "' no es un numero valido.";
        }
    }

    @Override
    public boolean isQuit(String request) {
        return request != null && EchoServer.QUIT_MESSAGE.equals(request.trim());
    }
}
