package edu.escuelaing.tdse.networking.webapp;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Peticion HTTP entrante, ya separada en sus partes utiles.
 *
 * <p>Solo se interpreta la <b>linea de peticion</b> ({@code GET /square?number=5 HTTP/1.1}),
 * que es lo unico que necesitan tanto los recursos estaticos como los servicios. Los
 * encabezados se leen del socket para vaciar el flujo, pero esta aplicacion no depende de
 * ninguno: cada peticion se atiende sin recordar nada de la anterior.
 *
 * @param method  metodo HTTP en mayusculas ({@code GET}, {@code POST}, ...)
 * @param target  objetivo completo tal como llego, con query incluida
 * @param version version declarada por el cliente ({@code HTTP/1.1})
 * @param path    ruta sin query ni fragmento, todavia <b>sin decodificar</b>
 * @param query   parametros de la query ya decodificados (URL-decode)
 */
public record HttpRequest(String method, String target, String version, String path,
                          Map<String, String> query) {

    /** Linea de peticion mal formada: no se pudo separar metodo y objetivo. */
    public static class MalformedRequestException extends Exception {
        public MalformedRequestException(String message) {
            super(message);
        }
    }

    /**
     * Interpreta la linea de peticion.
     *
     * @param requestLine linea completa, p. ej. {@code GET /hello?name=Ana HTTP/1.1}
     * @throws MalformedRequestException si no tiene al menos metodo y objetivo
     */
    public static HttpRequest parse(String requestLine) throws MalformedRequestException {
        if (requestLine == null || requestLine.isBlank()) {
            throw new MalformedRequestException("Linea de peticion vacia.");
        }
        String[] parts = requestLine.trim().split("\\s+");
        if (parts.length < 2) {
            throw new MalformedRequestException("Se esperaba 'METODO objetivo [version]'.");
        }
        String method = parts[0].toUpperCase();
        String target = parts[1];
        String version = parts.length > 2 ? parts[2] : "HTTP/1.0";

        // El fragmento (#) no deberia llegar al servidor, pero se recorta por si acaso.
        String withoutFragment = cutAt(target, '#');
        String path = cutAt(withoutFragment, '?');
        String rawQuery = withoutFragment.length() > path.length()
                ? withoutFragment.substring(path.length() + 1)
                : "";

        return new HttpRequest(method, target, version, path, parseQuery(rawQuery));
    }

    private static String cutAt(String text, char delimiter) {
        int index = text.indexOf(delimiter);
        return index < 0 ? text : text.substring(0, index);
    }

    /**
     * Convierte {@code a=1&b=hola%20mundo} en un mapa con los valores ya decodificados.
     *
     * <p>La decodificacion ocurre aqui, una sola vez y antes de cualquier validacion: si se
     * validara el texto crudo, un {@code %2E%2E} pasaria el filtro y se convertiria en
     * {@code ..} despues.
     */
    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (rawQuery.isEmpty()) {
            return parameters;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String name = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            // Un porcentaje suelto (p. ej. "100%") hace fallar al decodificador: en ese caso se
            // conserva el texto original en lugar de tumbar la peticion completa.
            parameters.put(decode(name), decode(value));
        }
        return parameters;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    /** Valor decodificado del parametro, o {@code null} si no vino en la query. */
    public String queryParam(String name) {
        return query.get(name);
    }

    /** {@code true} si el metodo es GET o HEAD, los dos unicos que atiende la aplicacion. */
    public boolean isReadMethod() {
        return "GET".equals(method) || "HEAD".equals(method);
    }
}
