package edu.escuelaing.tdse.networking.webapp;

import edu.escuelaing.tdse.networking.web.HttpResponse;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Aplicacion web secuencial sobre sockets: punto de entrada del laboratorio.
 *
 * <p>Extiende el servidor de archivos del ejercicio 4.5.1 con servicios que devuelven JSON, pero
 * conserva su propiedad central: <b>un solo hilo, una conexion a la vez</b>. El bucle acepta una
 * conexion, la atiende completa, la cierra y vuelve a {@code accept()}. Mientras una peticion se
 * procesa, las demas esperan en la cola del sistema operativo.
 *
 * <p>Uso:
 * <pre>
 *   java -jar app.jar               # puerto 35000
 *   java -jar app.jar 8080          # puerto por argumento
 *   PORT=8080 java -jar app.jar     # puerto por variable de entorno
 * </pre>
 */
public class WebApplication {

    /** Puerto por defecto, el mismo de los ejercicios de sockets del taller. */
    public static final int DEFAULT_PORT = 35000;

    /**
     * Interfaz de escucha por defecto.
     *
     * <p>Escuchar en {@code 0.0.0.0} (todas las interfaces) y no en {@code 127.0.0.1} es lo que
     * permite que la instancia EC2 responda desde fuera: atado a loopback, el servidor solo se
     * veria desde la propia maquina y la prueba desde el navegador daria un tiempo de espera
     * agotado imposible de distinguir de un security group mal configurado.
     */
    public static final String DEFAULT_BIND_ADDRESS = "0.0.0.0";

    /** Variable de entorno que define el puerto; es como lo fija la unidad de systemd en EC2. */
    public static final String PORT_ENV = "PORT";

    /** Propiedad del sistema equivalente: {@code -Dapp.port=8080}. */
    public static final String PORT_PROPERTY = "app.port";

    /**
     * Tiempo maximo esperando la peticion de un cliente ya conectado.
     *
     * <p>En un servidor secuencial esto no es un detalle menor: un cliente que abre la conexion y
     * no envia nada bloquearia al unico hilo para siempre y con el a todos los demas usuarios.
     */
    private static final int READ_TIMEOUT_MILLIS = 15_000;

    private static final int BACKLOG = 50;

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final RequestHandler handler;
    private final String bindAddress;

    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public WebApplication() {
        this(new RequestHandler(), DEFAULT_BIND_ADDRESS);
    }

    public WebApplication(RequestHandler handler, String bindAddress) {
        this.handler = handler;
        this.bindAddress = bindAddress;
    }

    public static void main(String[] args) {
        int port;
        try {
            port = resolvePort(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Puerto invalido: " + e.getMessage());
            System.err.println("Uso: java -jar app.jar [puerto]   (o la variable de entorno "
                    + PORT_ENV + ")");
            System.exit(2);
            return;
        }

        WebApplication application = new WebApplication();
        // Permite que Ctrl+C o 'systemctl stop' cierren el socket de escucha en orden.
        Runtime.getRuntime().addShutdownHook(new Thread(application::stop, "shutdown"));
        try {
            application.start(port);
        } catch (IOException e) {
            System.err.println("No fue posible escuchar en el puerto " + port + ": "
                    + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Determina el puerto de escucha.
     *
     * <p>El orden es argumento, propiedad del sistema, variable de entorno y por ultimo
     * {@value #DEFAULT_PORT}. El puerto <b>no esta quemado en el codigo</b> porque en EC2 se decide
     * al desplegar: en Linux un proceso sin privilegios no puede escuchar por debajo del 1024, asi
     * que la aplicacion corre en un puerto alto y el security group decide si se expone.
     *
     * @throws IllegalArgumentException si el valor no es un entero entre 0 y 65535
     */
    static int resolvePort(String[] args) {
        String value = null;
        if (args != null && args.length > 0 && !args[0].isBlank()) {
            value = args[0];
        } else if (System.getProperty(PORT_PROPERTY) != null) {
            value = System.getProperty(PORT_PROPERTY);
        } else if (System.getenv(PORT_ENV) != null && !System.getenv(PORT_ENV).isBlank()) {
            value = System.getenv(PORT_ENV);
        }
        if (value == null) {
            return DEFAULT_PORT;
        }

        int port;
        try {
            port = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + value.trim() + "' no es un numero.");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException(port + " esta fuera del rango 0-65535.");
        }
        return port;
    }

    /**
     * Escucha en {@code port} y atiende peticiones una tras otra hasta {@link #stop()}.
     *
     * @param port puerto de escucha; 0 deja que el sistema asigne uno libre (lo usan las pruebas)
     */
    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port, BACKLOG, InetAddress.getByName(bindAddress));
        running = true;
        System.out.println("Aplicacion web escuchando en " + bindAddress + ":" + getPort());
        System.out.println("Pagina de inicio: http://localhost:" + getPort() + "/");
        System.out.println("Modo secuencial: una conexion a la vez, sin hilos.");

        while (running) {
            try (Socket client = serverSocket.accept()) {
                client.setSoTimeout(READ_TIMEOUT_MILLIS);
                serve(client);
            } catch (SocketException e) {
                // Salida normal cuando stop() cierra el socket de escucha.
                if (running) {
                    log("Error de socket: " + e.getMessage());
                }
            } catch (IOException | RuntimeException e) {
                // Ninguna falla de una conexion puede tumbar el bucle: el servidor tiene que
                // seguir atendiendo a los demas usuarios despues de una peticion mal formada.
                log("Error atendiendo la peticion: " + e);
            }
        }
        System.out.println("Aplicacion web detenida.");
    }

    /**
     * Atiende una peticion completa sobre una conexion.
     *
     * <p>El socket se cierra al salir (lo cierra el {@code try-with-resources} de quien llama) y
     * con el sus flujos; por eso cada respuesta declara {@code Connection: close}.
     */
    private void serve(Socket client) throws IOException {
        long startedAt = System.nanoTime();
        BufferedReader in = new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
        OutputStream out = new BufferedOutputStream(client.getOutputStream());

        String requestLine;
        try {
            requestLine = in.readLine();
        } catch (SocketTimeoutException e) {
            log("Cliente sin enviar peticion en " + READ_TIMEOUT_MILLIS + " ms; se cierra.");
            return;
        }
        if (requestLine == null || requestLine.isBlank()) {
            // El navegador abre y cierra conexiones de reserva sin llegar a pedir nada.
            return;
        }

        // Los encabezados se consumen hasta la linea en blanco. No se usan, pero hay que vaciarlos
        // del flujo antes de responder.
        String header;
        while ((header = in.readLine()) != null && !header.isEmpty()) {
            // Sin cuerpo declarado no hay nada mas que leer despues de esta linea.
        }

        boolean headRequest = false;
        HttpResponse response;
        try {
            HttpRequest request = HttpRequest.parse(requestLine);
            headRequest = "HEAD".equals(request.method());
            response = handler.handle(request);
        } catch (HttpRequest.MalformedRequestException e) {
            response = HttpResponse.error(400, "Bad Request", "La peticion no es valida.");
        }

        out.write(response.headerBytes());
        if (!headRequest) {
            out.write(response.body());
        }
        out.flush();

        long millis = (System.nanoTime() - startedAt) / 1_000_000;
        log(requestLine + "  ->  " + response.status() + " " + response.reason()
                + " [" + response.contentType() + ", " + response.body().length + " bytes, "
                + millis + " ms]");
    }

    /** Puerto real de escucha; util cuando se arranco con el puerto 0. */
    public int getPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? -1 : socket.getLocalPort();
    }

    /** Detiene el bucle de aceptacion y libera el puerto. */
    public void stop() {
        running = false;
        ServerSocket socket = serverSocket;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error al cerrar el servidor: " + e.getMessage());
            }
        }
    }

    /** Registro con marca de tiempo; es la evidencia del orden en que se atienden las peticiones. */
    private static void log(String message) {
        System.out.println(LocalDateTime.now().format(LOG_TIME) + "  " + message);
    }
}
