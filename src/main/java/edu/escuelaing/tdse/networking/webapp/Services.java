package edu.escuelaing.tdse.networking.webapp;

import edu.escuelaing.tdse.networking.web.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Los servicios "hardcodeados" de la aplicacion.
 *
 * <p>Cada metodo publico corresponde a una URL especial reconocida con un {@code switch} explicito
 * en {@link RequestHandler}. No hay registro de rutas, ni anotaciones, ni reflexion: eso es
 * justamente lo que un framework generalizaria despues.
 *
 * <p>Los servicios son <b>sin estado</b>: no guardan nada del cliente entre peticiones. Lo unico
 * que sobrevive es el instante de arranque, que sirve para reportar el tiempo en linea en
 * {@code /health} y no es informacion de ningun usuario.
 */
public class Services {

    /** Limite del nombre aceptado por el saludo; evita respuestas absurdamente grandes. */
    static final int MAX_NAME_LENGTH = 60;

    /** Tope del retardo de {@code /slow}, para que nadie deje el servidor ocupado un dia entero. */
    static final int MAX_SLOW_SECONDS = 10;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final Clock clock;
    private final long startedAtMillis;

    public Services() {
        this(Clock.systemDefaultZone());
    }

    /**
     * @param clock reloj del servidor; se inyecta para poder fijar la hora en las pruebas
     */
    public Services(Clock clock) {
        this.clock = clock;
        this.startedAtMillis = clock.millis();
    }

    /**
     * {@code GET /hello?name=Ana} — saludo con el nombre recibido.
     *
     * @return 200 con el saludo en JSON, o 400 si el nombre falta, viene vacio o es demasiado largo
     */
    public HttpResponse greeting(HttpRequest request) {
        String name = request.queryParam("name");
        if (name == null) {
            return error(400, "Bad Request", "missing_parameter",
                    "Falta el parametro obligatorio 'name'.");
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return error(400, "Bad Request", "empty_parameter",
                    "El parametro 'name' no puede estar vacio.");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            return error(400, "Bad Request", "parameter_too_long",
                    "El parametro 'name' admite maximo " + MAX_NAME_LENGTH + " caracteres.");
        }
        // 'trimmed' viene del cliente: Json lo escapa al construir el documento.
        return ok(Json.object()
                .put("service", "greeting")
                .put("name", trimmed)
                .put("message", "Hola, " + trimmed + ". Este saludo lo genero el servidor Java.")
                .put("timestamp", now().format(ISO)));
    }

    /**
     * {@code GET /square?number=5} — cuadrado del numero recibido.
     *
     * <p>Es el ejercicio 4.3.1 de la guia de sockets, ahora expuesto por HTTP en lugar de por un
     * protocolo de texto propio.
     *
     * @return 200 con la entrada y su cuadrado, o 400 si el parametro falta o no es un numero
     */
    public HttpResponse square(HttpRequest request) {
        String raw = request.queryParam("number");
        if (raw == null) {
            return error(400, "Bad Request", "missing_parameter",
                    "Falta el parametro obligatorio 'number'.");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return error(400, "Bad Request", "empty_parameter",
                    "El parametro 'number' no puede estar vacio.");
        }

        double number;
        try {
            number = Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            // El valor recibido se devuelve para que el usuario vea que fue lo que se interpreto;
            // al escaparlo, un texto como 12" no puede romper el JSON.
            return error(400, "Bad Request", "invalid_number",
                    "El valor '" + trimmed + "' no es un numero valido.");
        }
        if (!Double.isFinite(number)) {
            return error(400, "Bad Request", "invalid_number",
                    "El parametro 'number' debe ser un numero finito.");
        }

        double result = number * number;
        if (!Double.isFinite(result)) {
            return error(400, "Bad Request", "out_of_range",
                    "El cuadrado de ese numero excede el rango representable.");
        }
        return ok(Json.object()
                .put("service", "square")
                .put("input", number)
                .put("result", result));
    }

    /**
     * {@code GET /time} — hora actual del servidor.
     *
     * <p>Sirve para comprobar que el dato viene de la maquina remota: si la instancia EC2 esta en
     * UTC y el navegador en Bogota, la hora mostrada no coincide con la del reloj local.
     */
    public HttpResponse serverTime(HttpRequest request) {
        ZonedDateTime now = now();
        return ok(Json.object()
                .put("service", "server-time")
                .put("iso8601", now.format(ISO))
                .put("epochMillis", now.toInstant().toEpochMilli())
                .put("zone", now.getZone().getId()));
    }

    /**
     * {@code GET /health} — confirma que el proceso esta vivo y respondiendo.
     *
     * <p>Es el primer chequeo del despliegue: {@code curl localhost:PUERTO/health} desde dentro de
     * la instancia separa "la aplicacion no arranco" de "el security group no deja entrar".
     */
    public HttpResponse health(HttpRequest request) {
        return ok(Json.object()
                .put("status", "ok")
                .put("service", "health")
                .put("uptimeSeconds", (clock.millis() - startedAtMillis) / 1000));
    }

    /**
     * {@code GET /slow?seconds=5} — servicio deliberadamente lento.
     *
     * <p>Es el instrumento de medicion de la Fase 5: mientras este servicio duerme, el unico hilo
     * del servidor esta ocupado y cualquier otra peticion queda esperando en la cola de
     * {@code accept()}. Los instantes de inicio y fin que devuelve permiten demostrar en la
     * evidencia que la segunda ventana del navegador arranco justo cuando la primera termino.
     */
    public HttpResponse slow(HttpRequest request) {
        String raw = request.queryParam("seconds");
        double seconds = 3;
        if (raw != null && !raw.isBlank()) {
            try {
                seconds = Double.parseDouble(raw.trim());
            } catch (NumberFormatException e) {
                return error(400, "Bad Request", "invalid_number",
                        "El valor '" + raw.trim() + "' no es un numero de segundos valido.");
            }
        }
        if (!(seconds >= 0) || seconds > MAX_SLOW_SECONDS) {
            return error(400, "Bad Request", "out_of_range",
                    "El parametro 'seconds' debe estar entre 0 y " + MAX_SLOW_SECONDS + ".");
        }

        ZonedDateTime startedAt = now();
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(503, "Service Unavailable", "interrupted",
                    "La solicitud se interrumpio antes de completarse.");
        }
        return ok(Json.object()
                .put("service", "slow")
                .put("seconds", seconds)
                .put("startedAt", startedAt.format(ISO))
                .put("finishedAt", now().format(ISO)));
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now(clock);
    }

    private static HttpResponse ok(Json body) {
        return json(200, "OK", body);
    }

    /** Respuesta JSON con estado arbitrario. */
    public static HttpResponse json(int status, String reason, Json body) {
        return new HttpResponse(status, reason, "application/json; charset=UTF-8",
                body.build().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Error de un servicio, en JSON.
     *
     * <p>Los errores de los servicios no pueden responder la pagina HTML de error de los recursos
     * estaticos: el cliente hace {@code response.json()} y solo entiende este formato.
     *
     * @param code    identificador estable del error, pensado para el codigo cliente
     * @param message texto para la persona; nunca incluye detalles internos ni trazas
     */
    public static HttpResponse error(int status, String reason, String code, String message) {
        return json(status, reason, Json.object()
                .put("error", code)
                .put("message", message)
                .put("status", status));
    }
}
