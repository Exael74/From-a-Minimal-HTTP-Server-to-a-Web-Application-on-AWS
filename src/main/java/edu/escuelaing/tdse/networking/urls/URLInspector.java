package edu.escuelaing.tdse.networking.urls;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Etapa 1 - Ejercicio 1.
 *
 * <p>Crea un objeto {@link URL} e imprime los valores devueltos por los ocho metodos
 * de lectura de sus componentes: {@code getProtocol}, {@code getAuthority},
 * {@code getHost}, {@code getPort}, {@code getPath}, {@code getQuery},
 * {@code getFile} y {@code getRef}.
 *
 * <p>Uso: {@code java URLInspector [url]}. Si no se pasa argumento se usa
 * {@link #DEFAULT_URL}.
 */
public class URLInspector {

    /** URL de ejemplo, con puerto, ruta, query y fragmento, para que ningun metodo salga vacio. */
    public static final String DEFAULT_URL =
            "http://ldbn.escuelaing.edu.co:80/docs/index.html?lang=es&page=2#seccion3";

    public static void main(String[] args) {
        String site = args.length > 0 ? args[0] : DEFAULT_URL;
        try {
            URL url = buildURL(site);
            System.out.println("URL analizada: " + url);
            System.out.println();
            components(url).forEach(
                    (name, value) -> System.out.printf("%-14s %s%n", name + ":", value));
        } catch (MalformedURLException | URISyntaxException e) {
            System.err.println("La direccion no es una URL valida: " + site);
            System.err.println(e.getMessage());
        }
    }

    /**
     * Construye la URL a partir de un texto.
     *
     * <p>Se pasa por {@link URI} porque los constructores de {@link URL} estan deprecados
     * desde Java 20; el resultado es el mismo objeto {@code URL} del enunciado.
     */
    public static URL buildURL(String site) throws MalformedURLException, URISyntaxException {
        return new URI(site).toURL();
    }

    /**
     * Devuelve los ocho componentes de la URL, en el orden en que los pide el enunciado.
     *
     * @return mapa ordenado nombre-del-metodo -> valor devuelto
     */
    public static Map<String, String> components(URL url) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("getProtocol", show(url.getProtocol()));
        values.put("getAuthority", show(url.getAuthority()));
        values.put("getHost", show(url.getHost()));
        // getPort devuelve -1 cuando la URL no declara el puerto explicitamente.
        values.put("getPort", url.getPort() == -1
                ? "-1 (no declarado; por defecto " + url.getDefaultPort() + ")"
                : String.valueOf(url.getPort()));
        values.put("getPath", show(url.getPath()));
        values.put("getQuery", show(url.getQuery()));
        values.put("getFile", show(url.getFile()));
        values.put("getRef", show(url.getRef()));
        return values;
    }

    /** Hace visibles los valores nulos o vacios que devuelven varios de estos metodos. */
    private static String show(String value) {
        if (value == null) {
            return "(null)";
        }
        return value.isEmpty() ? "(vacio)" : value;
    }
}
