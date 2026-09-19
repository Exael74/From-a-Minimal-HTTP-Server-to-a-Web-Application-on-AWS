package edu.escuelaing.tdse.networking.urls;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Etapa 1 - Ejercicio 2.
 *
 * <p>Pequena aplicacion de "navegador": pide una URL al usuario, imprime los encabezados de
 * respuesta HTTP (seccion 1.3 del taller), guarda el cuerpo devuelto en {@code result.html}
 * y abre ese archivo en el navegador del sistema.
 *
 * <p>El cuerpo se copia byte a byte, de modo que tambien funciona si el recurso solicitado
 * no es texto.
 */
public class MiniBrowser {

    /** Archivo donde se guarda la respuesta, tal como lo pide el enunciado. */
    public static final String OUTPUT_FILE = "result.html";

    private static final int TIMEOUT_MS = 10_000;

    public static void main(String[] args) {
        try {
            String site = args.length > 0 ? args[0] : askForURL();
            if (site == null || site.isBlank()) {
                System.out.println("No se ingreso ninguna URL. Fin.");
                return;
            }
            Path result = download(site.trim(), Path.of(OUTPUT_FILE));
            System.out.println();
            System.out.println("Contenido guardado en: " + result.toAbsolutePath());
            open(result);
        } catch (Exception e) {
            System.err.println("No fue posible descargar la pagina: " + e.getMessage());
        }
    }

    /** Pide la direccion por consola. */
    private static String askForURL() throws IOException {
        BufferedReader keyboard = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.print("Ingrese la URL (ej. https://www.google.com/): ");
        return keyboard.readLine();
    }

    /**
     * Abre la conexion, imprime los encabezados de respuesta y escribe el cuerpo en {@code target}.
     *
     * @return la ruta del archivo escrito
     */
    public static Path download(String site, Path target) throws Exception {
        URL url = URLInspector.buildURL(site);
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        // Algunos servidores rechazan las peticiones sin User-Agent.
        connection.setRequestProperty("User-Agent", "MiniBrowser/1.0 (Taller TDSE)");
        connection.connect();

        printHeaders(connection);

        // El flujo del cuerpo se obtiene de la conexion, no directamente de la URL.
        try (InputStream in = connection.getInputStream();
             OutputStream out = Files.newOutputStream(target)) {
            in.transferTo(out);
        }
        return target;
    }

    /** Imprime los encabezados de respuesta devueltos por el servidor. */
    private static void printHeaders(URLConnection connection) {
        if (connection instanceof HttpURLConnection http) {
            try {
                System.out.println("Codigo de respuesta: " + http.getResponseCode()
                        + " " + http.getResponseMessage());
            } catch (IOException e) {
                System.err.println("No se pudo leer el codigo de respuesta: " + e.getMessage());
            }
        }
        System.out.println("--- Encabezados de respuesta ---");
        for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
            String headerName = entry.getKey();
            // Un nombre nulo representa la linea de estado HTTP.
            System.out.println((headerName == null ? "(linea de estado)" : headerName)
                    + ": " + String.join(", ", entry.getValue()));
        }
        System.out.println("--------------------------------");
    }

    /** Abre el archivo descargado en el navegador por defecto del sistema. */
    private static void open(Path file) {
        if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            System.out.println("Abra el archivo manualmente en su navegador.");
            return;
        }
        try {
            Desktop.getDesktop().browse(file.toAbsolutePath().toUri());
        } catch (IOException e) {
            System.out.println("Abra el archivo manualmente en su navegador: " + e.getMessage());
        }
    }
}
