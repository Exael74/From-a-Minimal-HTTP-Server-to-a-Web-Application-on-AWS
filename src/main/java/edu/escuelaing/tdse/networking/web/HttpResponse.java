package edu.escuelaing.tdse.networking.web;

import java.nio.charset.StandardCharsets;

/**
 * Respuesta HTTP lista para escribirse en el socket.
 *
 * @param status codigo de estado (200, 404, ...)
 * @param reason frase asociada al codigo ("OK", "Not Found", ...)
 * @param contentType valor del encabezado {@code Content-Type}
 * @param body cuerpo en bytes; se maneja como binario para poder servir imagenes
 */
public record HttpResponse(int status, String reason, String contentType, byte[] body) {

    /** Construye una respuesta de texto HTML con el estado indicado. */
    public static HttpResponse html(int status, String reason, String html) {
        return new HttpResponse(status, reason, MimeTypes.of("x.html"),
                html.getBytes(StandardCharsets.UTF_8));
    }

    /** Construye una pagina de error simple con el mensaje dado. */
    public static HttpResponse error(int status, String reason, String message) {
        String page = "<!doctype html><html><head><meta charset=\"UTF-8\">"
                + "<title>" + status + " " + reason + "</title></head>"
                + "<body><h1>" + status + " " + reason + "</h1><p>" + message + "</p></body></html>";
        return html(status, reason, page);
    }

    /**
     * Serializa la linea de estado y los encabezados.
     *
     * <p>Se declara {@code Connection: close} porque este servidor cierra el socket despues de
     * cada peticion (atiende peticiones consecutivas, no persistentes).
     */
    public byte[] headerBytes() {
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        // Los encabezados HTTP viajan en US-ASCII; solo el cuerpo puede ser binario o UTF-8.
        return headers.getBytes(StandardCharsets.US_ASCII);
    }
}
