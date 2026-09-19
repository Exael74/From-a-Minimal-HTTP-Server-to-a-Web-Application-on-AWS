package edu.escuelaing.tdse.networking.sockets;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/**
 * Etapa 2 - Ejercicio 4.3.2: recibe un numero y devuelve el resultado de la operacion activa.
 *
 * <p>Un mensaje que empiece por {@value #COMMAND_PREFIX} cambia la operacion. Se soportan
 * {@code sin}, {@code cos} y {@code tan}, y la operacion por defecto es el <b>coseno</b>.
 *
 * <p>Ejemplo del enunciado: al inicio {@code 0} produce {@code 1} y {@code pi/2} produce
 * {@code 0}; tras {@code fun:sin}, {@code 0} produce {@code 0}.
 *
 * <p>Cada conexion tiene su propia instancia, asi que la operacion activa es estado por cliente.
 */
public class MathFunctionProtocol implements LineProtocol {

    /** Prefijo que identifica un cambio de operacion. */
    public static final String COMMAND_PREFIX = "fun:";

    /** Operacion activa al iniciar la conexion. */
    public static final String DEFAULT_FUNCTION = "cos";

    private static final Map<String, DoubleUnaryOperator> FUNCTIONS = new LinkedHashMap<>();

    static {
        FUNCTIONS.put("sin", Math::sin);
        FUNCTIONS.put("cos", Math::cos);
        FUNCTIONS.put("tan", Math::tan);
    }

    private String currentFunction = DEFAULT_FUNCTION;

    /** Operacion activa en este momento. */
    public String getCurrentFunction() {
        return currentFunction;
    }

    @Override
    public String process(String request) {
        String value = request == null ? "" : request.trim();

        if (value.isEmpty()) {
            return "Error: ingrese un numero o " + COMMAND_PREFIX + "sin|cos|tan.";
        }
        if (isQuit(value)) {
            return "Response: " + EchoServer.QUIT_MESSAGE;
        }
        if (value.toLowerCase().startsWith(COMMAND_PREFIX)) {
            return changeFunction(value.substring(COMMAND_PREFIX.length()).trim());
        }
        return evaluate(value);
    }

    /** Cambia la operacion activa si el nombre recibido es una de las soportadas. */
    private String changeFunction(String name) {
        String requested = name.toLowerCase();
        if (!FUNCTIONS.containsKey(requested)) {
            return "Error: operacion '" + name + "' no soportada. Use "
                    + String.join(", ", FUNCTIONS.keySet()) + ".";
        }
        currentFunction = requested;
        return "Operacion cambiada a " + currentFunction + ".";
    }

    /** Aplica la operacion activa al numero recibido. */
    private String evaluate(String value) {
        try {
            double number = Numbers.parse(value);
            return Numbers.format(FUNCTIONS.get(currentFunction).applyAsDouble(number));
        } catch (NumberFormatException e) {
            return "Error: '" + value + "' no es un numero valido.";
        }
    }

    @Override
    public boolean isQuit(String request) {
        return request != null && EchoServer.QUIT_MESSAGE.equals(request.trim());
    }
}
