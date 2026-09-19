package edu.escuelaing.tdse.networking.webapp;

import edu.escuelaing.tdse.networking.web.HttpResponse;

/**
 * Decide que responder a cada peticion.
 *
 * <p>Este es el "router" del laboratorio y es deliberadamente tonto: un {@code switch} sobre la
 * ruta exacta. No hay tabla de rutas, patrones con comodines, parametros en el path ni registro
 * dinamico de manejadores. Agregar un servicio significa <b>editar el switch y recompilar</b>.
 * Esa es exactamente la limitacion que un framework de rutas viene a resolver.
 *
 * <p>Separar esta clase del socket permite probar todas las decisiones de ruteo, estado y
 * Content-Type sin abrir un puerto.
 */
public class RequestHandler {

    public static final String PATH_GREETING = "/hello";
    public static final String PATH_SQUARE = "/square";
    public static final String PATH_TIME = "/time";
    public static final String PATH_HEALTH = "/health";
    public static final String PATH_SLOW = "/slow";

    private final PublicResources resources;
    private final Services services;

    public RequestHandler() {
        this(new PublicResources(), new Services());
    }

    public RequestHandler(PublicResources resources, Services services) {
        this.resources = resources;
        this.services = services;
    }

    /**
     * Atiende una peticion ya interpretada.
     *
     * @return la respuesta lista para escribirse en el socket; nunca {@code null}
     */
    public HttpResponse handle(HttpRequest request) {
        String path = normalizeTrailingSlash(request.path());

        // El metodo se valida antes de repartir. Los servicios aceptan solo GET, como pide el
        // enunciado; los recursos estaticos aceptan ademas HEAD, que es GET sin cuerpo y ya venia
        // soportado desde el servidor de archivos del ejercicio 4.5.1.
        if (isServicePath(path)) {
            if (!"GET".equals(request.method())) {
                return Services.error(405, "Method Not Allowed", "method_not_allowed",
                        "Este servicio solo responde a GET.");
            }
        } else if (!request.isReadMethod()) {
            return HttpResponse.error(405, "Method Not Allowed",
                    "Este servidor solo soporta GET y HEAD.");
        }

        return switch (path) {
            case PATH_GREETING -> services.greeting(request);
            case PATH_SQUARE -> services.square(request);
            case PATH_TIME -> services.serverTime(request);
            case PATH_HEALTH -> services.health(request);
            case PATH_SLOW -> services.slow(request);
            default -> resources.resolve(path);
        };
    }

    /** {@code true} si la ruta corresponde a una de las URLs especiales. */
    public static boolean isServicePath(String path) {
        return switch (path) {
            case PATH_GREETING, PATH_SQUARE, PATH_TIME, PATH_HEALTH, PATH_SLOW -> true;
            default -> false;
        };
    }

    /**
     * Quita la barra final para que {@code /health/} llegue al mismo servicio que {@code /health}.
     * La raiz ({@code /}) se deja intacta porque es la pagina de inicio.
     */
    private static String normalizeTrailingSlash(String path) {
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
