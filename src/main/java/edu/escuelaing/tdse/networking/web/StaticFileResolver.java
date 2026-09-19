package edu.escuelaing.tdse.networking.web;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Etapa 2 - Ejercicio 4.5.1: traduce la ruta pedida en una peticion HTTP al archivo del disco
 * que debe devolverse.
 *
 * <p>Esta separado del socket para poder probar la logica de resolucion sin abrir puertos.
 */
public class StaticFileResolver {

    /** Archivo que se devuelve cuando se pide un directorio. */
    public static final String INDEX_FILE = "index.html";

    private final Path root;

    /**
     * @param root directorio raiz publico; nada fuera de el puede servirse
     */
    public StaticFileResolver(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path getRoot() {
        return root;
    }

    /**
     * Resuelve el objetivo de la peticion.
     *
     * @param requestTarget ruta pedida, tal como llega en la linea de peticion (p. ej.
     *                      {@code /img/logo.png?v=2})
     * @return 200 con el contenido del archivo, 404 si no existe y 403 si intenta salir de la raiz
     */
    public HttpResponse resolve(String requestTarget) {
        String target = requestTarget == null ? "/" : requestTarget;

        // La query no forma parte del nombre del archivo.
        int query = target.indexOf('?');
        if (query >= 0) {
            target = target.substring(0, query);
        }
        // El fragmento (#) no llega al servidor, pero se recorta por si acaso.
        int fragment = target.indexOf('#');
        if (fragment >= 0) {
            target = target.substring(0, fragment);
        }
        target = URLDecoder.decode(target, StandardCharsets.UTF_8);

        if (target.isEmpty() || "/".equals(target)) {
            target = "/" + INDEX_FILE;
        }

        Path file;
        try {
            // El "/" inicial se quita para resolver siempre en forma relativa a la raiz.
            file = root.resolve(target.substring(1)).normalize();
        } catch (InvalidPathException e) {
            return HttpResponse.error(400, "Bad Request", "Ruta invalida: " + requestTarget);
        }

        // Sin esta verificacion, una peticion como /../../secret.txt saldria del directorio publico.
        if (!file.startsWith(root)) {
            return HttpResponse.error(403, "Forbidden",
                    "La ruta solicitada esta fuera del directorio publico.");
        }

        if (Files.isDirectory(file)) {
            file = file.resolve(INDEX_FILE);
        }
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            return HttpResponse.error(404, "Not Found",
                    "El recurso " + target + " no existe en el servidor.");
        }

        try {
            byte[] content = Files.readAllBytes(file);
            return new HttpResponse(200, "OK", MimeTypes.of(file.getFileName().toString()), content);
        } catch (IOException e) {
            return HttpResponse.error(500, "Internal Server Error",
                    "No fue posible leer el recurso: " + e.getMessage());
        }
    }
}
