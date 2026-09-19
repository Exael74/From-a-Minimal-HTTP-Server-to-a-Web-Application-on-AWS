package edu.escuelaing.tdse.networking.webapp;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Construccion manual de objetos JSON planos.
 *
 * <p>El laboratorio prohibe usar un framework, asi que el JSON se arma a mano. Lo importante no
 * es el tamano de la clase sino el {@link #escape(String)}: cualquier valor que venga del cliente
 * (el nombre del saludo, el texto que no se pudo convertir a numero) se escapa antes de entrar al
 * documento. Sin eso, un nombre como {@code a"b} romperia el JSON y el cliente veria un error de
 * parseo en lugar de una respuesta.
 */
public final class Json {

    /** Separador de linea de JavaScript: JSON valido, pero rompe un script si no se escapa. */
    private static final char LINE_SEPARATOR = 0x2028;

    /** Separador de parrafo de JavaScript, mismo caso que {@link #LINE_SEPARATOR}. */
    private static final char PARAGRAPH_SEPARATOR = 0x2029;

    private final Map<String, String> fields = new LinkedHashMap<>();

    /** Inicia un objeto JSON vacio. */
    public static Json object() {
        return new Json();
    }

    /** Agrega un campo de texto; el valor se escapa y se encierra en comillas. */
    public Json put(String name, String value) {
        fields.put(name, value == null ? "null" : "\"" + escape(value) + "\"");
        return this;
    }

    /** Agrega un campo numerico usando el formato de {@link #number(double)}. */
    public Json put(String name, double value) {
        fields.put(name, number(value));
        return this;
    }

    /** Agrega un campo numerico entero. */
    public Json put(String name, long value) {
        fields.put(name, String.valueOf(value));
        return this;
    }

    /** Agrega un campo booleano. */
    public Json put(String name, boolean value) {
        fields.put(name, String.valueOf(value));
        return this;
    }

    /** Serializa el objeto completo. */
    public String build() {
        StringBuilder json = new StringBuilder("{");
        String separator = "";
        for (Map.Entry<String, String> field : fields.entrySet()) {
            json.append(separator)
                    .append('"').append(escape(field.getKey())).append("\":")
                    .append(field.getValue());
            separator = ",";
        }
        return json.append('}').toString();
    }

    @Override
    public String toString() {
        return build();
    }

    /**
     * Escapa un texto para que pueda viajar dentro de una cadena JSON.
     *
     * <p>Cubre los escapes obligatorios de la especificacion (comilla, barra invertida y los
     * caracteres de control por debajo de 0x20) y ademas U+2028/U+2029, que son validos en JSON
     * pero terminan la linea cuando el navegador interpreta la respuesta como JavaScript.
     *
     * <p>Las constantes se comparan por su valor numerico: escritas como escape unicode en el
     * codigo fuente, el preprocesador de Java las convertiria en un salto de linea real.
     */
    public static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20 || c == LINE_SEPARATOR || c == PARAGRAPH_SEPARATOR) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    /**
     * Formatea un numero para JSON evitando la notacion cientifica y el {@code .0} innecesario.
     *
     * <p>Asi {@code 5} responde {@code 25} y no {@code 25.0}, y un valor grande no aparece como
     * {@code 1.0E20}, que es JSON valido pero incomodo de leer en la evidencia.
     */
    public static String number(double value) {
        if (!Double.isFinite(value)) {
            // JSON no admite NaN ni Infinity: se reporta como null antes que producir algo invalido.
            return "null";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
