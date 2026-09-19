package edu.escuelaing.tdse.networking.web;

import java.util.Map;

/**
 * Traduce la extension de un archivo al valor del encabezado {@code Content-Type}.
 *
 * <p>Es lo que le permite al navegador saber si debe renderizar HTML, aplicar una hoja de
 * estilos o pintar una imagen, aunque por el socket solo viajen bytes.
 */
public final class MimeTypes {

    /** Tipo usado cuando la extension es desconocida: el navegador ofrecera descargar. */
    public static final String DEFAULT_TYPE = "application/octet-stream";

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=UTF-8"),
            Map.entry("htm", "text/html; charset=UTF-8"),
            Map.entry("css", "text/css; charset=UTF-8"),
            Map.entry("js", "text/javascript; charset=UTF-8"),
            Map.entry("json", "application/json; charset=UTF-8"),
            Map.entry("txt", "text/plain; charset=UTF-8"),
            Map.entry("csv", "text/csv; charset=UTF-8"),
            Map.entry("xml", "application/xml; charset=UTF-8"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("webp", "image/webp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"));

    private MimeTypes() {
    }

    /**
     * Devuelve el {@code Content-Type} correspondiente al nombre de archivo.
     *
     * @param fileName nombre o ruta del archivo
     * @return el tipo MIME, o {@value #DEFAULT_TYPE} si la extension no se reconoce
     */
    public static String of(String fileName) {
        if (fileName == null) {
            return DEFAULT_TYPE;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return DEFAULT_TYPE;
        }
        String extension = fileName.substring(dot + 1).toLowerCase();
        return TYPES.getOrDefault(extension, DEFAULT_TYPE);
    }
}
