package edu.escuelaing.tdse.networking.webapp;

import edu.escuelaing.tdse.networking.web.HttpResponse;
import edu.escuelaing.tdse.networking.web.MimeTypes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Sirve los recursos estaticos (HTML, JavaScript, CSS e imagenes) del directorio publico.
 *
 * <p>Los recursos viven en {@code src/main/resources/public}, asi que Maven los empaqueta
 * <b>dentro del jar</b> y se leen del classpath. Esa decision es la que hace que el despliegue en
 * EC2 sea copiar un solo archivo: no hay que sincronizar aparte una carpeta de recursos ni acertar
 * con el directorio de trabajo del servicio de systemd.
 *
 * <p>Todo se lee y se entrega como <b>bytes</b>. Una imagen que pase por un {@code Reader} se
 * corrompe: el decodificador de texto reemplaza cada secuencia que no es UTF-8 valido por el
 * caracter de reemplazo, y ademas el {@code Content-Length} dejaria de coincidir con lo enviado.
 */
public class PublicResources {

    /** Carpeta del classpath que contiene lo publicable. Nada fuera de ella puede servirse. */
    public static final String DEFAULT_ROOT = "public";

    /** Archivo que se devuelve cuando se pide la raiz o un directorio. */
    public static final String INDEX_FILE = "index.html";

    private final String root;

    public PublicResources() {
        this(DEFAULT_ROOT);
    }

    public PublicResources(String root) {
        this.root = root;
    }

    /**
     * Resuelve la ruta pedida contra el directorio publico.
     *
     * @param path ruta sin query, tal como llego en la linea de peticion (p. ej. {@code /logo.png})
     * @return 200 con los bytes del recurso; 400 si la ruta es invalida; 403 si intenta salir del
     *         directorio publico; 404 si no existe o si su extension no se sabe tipar
     */
    public HttpResponse resolve(String path) {
        String decoded;
        try {
            decoded = decode(path == null ? "/" : path);
        } catch (IllegalArgumentException e) {
            return HttpResponse.error(400, "Bad Request", "La ruta solicitada no es valida.");
        }
        // La barra invertida es separador de rutas en Windows: se rechaza para que el filtro de
        // segmentos de abajo no pueda esquivarse con "..\\..\\archivo".
        if (decoded.indexOf('\\') >= 0 || decoded.indexOf('\0') >= 0) {
            return HttpResponse.error(400, "Bad Request", "La ruta solicitada no es valida.");
        }

        String normalized;
        try {
            normalized = normalize(decoded);
        } catch (PathTraversalException e) {
            return HttpResponse.error(403, "Forbidden",
                    "La ruta solicitada esta fuera del directorio publico.");
        }

        String resource = normalized.isEmpty() ? INDEX_FILE : normalized;
        byte[] content;
        if (lastSegmentHasExtension(resource)) {
            content = read(resource);
        } else {
            // Una ruta sin extension se interpreta como directorio y se busca su indice, como hace
            // cualquier servidor web. Se intenta el indice antes que el recurso a secas porque en
            // el classpath un directorio tambien se puede abrir: devolveria su listado, no un
            // archivo servible.
            resource = resource + "/" + INDEX_FILE;
            content = read(resource);
        }
        if (content == null) {
            return HttpResponse.error(404, "Not Found",
                    "El recurso solicitado no existe en este servidor.");
        }

        String contentType = MimeTypes.of(resource);
        if (MimeTypes.DEFAULT_TYPE.equals(contentType)) {
            // Si no se sabe con que Content-Type anunciarlo, no se publica: el navegador solo
            // ofreceria descargar un archivo que este laboratorio nunca necesita servir.
            return HttpResponse.error(404, "Not Found",
                    "El recurso solicitado no esta disponible en este servidor.");
        }
        return new HttpResponse(200, "OK", contentType, content);
    }

    /** Se lanza cuando la ruta normalizada se sale del directorio publico. */
    static class PathTraversalException extends Exception {
    }

    /**
     * Normaliza la ruta a una forma relativa segura, sin {@code .} ni {@code ..}.
     *
     * <p>La normalizacion se hace recorriendo los segmentos con una pila en lugar de concatenar
     * texto: un {@code ..} saca el ultimo segmento y, si ya no hay ninguno que sacar, la peticion
     * esta intentando subir por encima de la raiz publica y se rechaza. Con esto,
     * {@code /../../etc/passwd} o {@code /%2e%2e/pom.xml} nunca llegan a convertirse en una
     * busqueda real de recurso.
     */
    static String normalize(String path) throws PathTraversalException {
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (segments.isEmpty()) {
                    throw new PathTraversalException();
                }
                segments.removeLast();
            } else {
                segments.addLast(segment);
            }
        }
        return String.join("/", segments);
    }

    /**
     * Decodifica los {@code %XX} de una ruta.
     *
     * <p>No se usa {@code URLDecoder}: ese metodo aplica las reglas de un formulario y convierte
     * {@code +} en espacio, lo que corromperia el nombre de un archivo que contenga un {@code +}.
     * En la ruta, {@code +} es un caracter literal.
     */
    static String decode(String path) {
        if (path.indexOf('%') < 0) {
            return path;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(path.length());
        int i = 0;
        while (i < path.length()) {
            if (path.charAt(i) == '%') {
                if (i + 2 >= path.length()) {
                    throw new IllegalArgumentException("Escape incompleto en la ruta.");
                }
                // parseInt lanza NumberFormatException (una IllegalArgumentException) si los dos
                // caracteres siguientes no son hexadecimales, que es justo lo que espera quien llama.
                bytes.write(Integer.parseInt(path.substring(i + 1, i + 3), 16));
                i += 3;
            } else {
                // Se copia el tramo completo sin escapes de una vez: hacerlo caracter por caracter
                // partiria en dos un par suplente y produciria bytes UTF-8 invalidos.
                int start = i;
                while (i < path.length() && path.charAt(i) != '%') {
                    i++;
                }
                byte[] literal = path.substring(start, i).getBytes(StandardCharsets.UTF_8);
                bytes.write(literal, 0, literal.length);
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /** Lee un recurso del classpath, o {@code null} si no existe. */
    private byte[] read(String resource) {
        String name = root + "/" + resource;
        ClassLoader loader = PublicResources.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(name)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private boolean lastSegmentHasExtension(String resource) {
        int slash = resource.lastIndexOf('/');
        return resource.indexOf('.', slash + 1) >= 0;
    }
}
