package edu.escuelaing.tdse.networking.sockets;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilidades de lectura y presentacion de numeros compartidas por los servidores de la Etapa 2.
 */
final class Numbers {

    /** Decimales a los que se redondea la respuesta antes de mostrarla. */
    private static final int SCALE = 10;

    /** Acepta expresiones como {@code pi}, {@code -pi}, {@code pi/2} o {@code PI / 4}. */
    private static final Pattern PI_EXPRESSION =
            Pattern.compile("^([+-]?)\\s*pi\\s*(?:/\\s*(\\d*\\.?\\d+))?$", Pattern.CASE_INSENSITIVE);

    private Numbers() {
    }

    /**
     * Convierte el texto recibido en un numero.
     *
     * <p>Ademas de los decimales normales, acepta la constante {@code pi} y divisiones simples
     * como {@code pi/2}, para poder reproducir los ejemplos del enunciado sin escribir
     * 1.5707963267948966 a mano.
     *
     * @throws NumberFormatException si el texto no representa un numero
     */
    static double parse(String text) {
        String value = text == null ? "" : text.trim();
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            // Puede tratarse de una expresion con pi; se intenta a continuacion.
        }

        Matcher matcher = PI_EXPRESSION.matcher(value);
        if (!matcher.matches()) {
            throw new NumberFormatException("No es un numero: " + value);
        }
        double result = "-".equals(matcher.group(1)) ? -Math.PI : Math.PI;
        String denominator = matcher.group(2);
        if (denominator != null) {
            double divisor = Double.parseDouble(denominator);
            if (divisor == 0) {
                throw new NumberFormatException("Division por cero: " + value);
            }
            result /= divisor;
        }
        return result;
    }

    /**
     * Formatea el resultado redondeando a {@value #SCALE} decimales.
     *
     * <p>El redondeo es lo que hace que {@code cos(pi/2)} se muestre como {@code 0} y no como
     * {@code 6.123233995736766E-17}: la aritmetica de punto flotante no representa pi de forma
     * exacta, asi que el coseno nunca da exactamente cero.
     */
    static String format(double value) {
        if (!Double.isFinite(value)) {
            return String.valueOf(value);
        }
        double rounded = BigDecimal.valueOf(value)
                .setScale(SCALE, RoundingMode.HALF_UP)
                .doubleValue();

        if (rounded == Math.rint(rounded) && Math.abs(rounded) < 1e15) {
            return String.valueOf((long) rounded);
        }
        return BigDecimal.valueOf(rounded).stripTrailingZeros().toPlainString();
    }
}
