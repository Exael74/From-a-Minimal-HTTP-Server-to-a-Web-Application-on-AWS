package edu.escuelaing.tdse.networking.urls;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Etapa 1 - Seccion 1.2 (Figura 1): lee una pagina de Internet abriendo un flujo de entrada
 * directamente sobre el objeto {@link URL} y lo consume linea por linea.
 *
 * <p>Uso: {@code java URLReader [url]}. Por defecto lee {@code http://www.google.com/}.
 */
public class URLReader {

    public static void main(String[] args) {
        String site = args.length > 0 ? args[0] : "http://www.google.com/";
        try {
            URL page = URLInspector.buildURL(site);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(page.openStream(), StandardCharsets.UTF_8))) {
                String inputLine;
                while ((inputLine = reader.readLine()) != null) {
                    System.out.println(inputLine);
                }
            }
        } catch (IOException x) {
            System.err.println(x);
        } catch (Exception e) {
            System.err.println("URL invalida: " + site);
        }
    }
}
