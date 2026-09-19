package edu.escuelaing.tdse.networking.sockets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Ejercicio 4.3.2: seno, coseno y tangente, con cambio de operacion mediante {@code fun:}. */
class MathFunctionProtocolTest {

    private MathFunctionProtocol protocol;

    @BeforeEach
    void setUp() {
        protocol = new MathFunctionProtocol();
    }

    @Test
    void deberiaUsarCosenoPorDefecto() {
        assertEquals("cos", protocol.getCurrentFunction());
    }

    /** Ejemplo textual del enunciado: al inicio, 0 produce 1 y pi/2 produce 0. */
    @Test
    void deberiaReproducirElEjemploDelEnunciado() {
        assertEquals("1", protocol.process("0"));
        assertEquals("0", protocol.process("pi/2"));

        assertEquals("Operacion cambiada a sin.", protocol.process("fun:sin"));
        assertEquals("0", protocol.process("0"));
        assertEquals("1", protocol.process("pi/2"));
    }

    @Test
    void deberiaCambiarACadaUnaDeLasOperacionesSoportadas() {
        protocol.process("fun:sin");
        assertEquals("sin", protocol.getCurrentFunction());

        protocol.process("fun:tan");
        assertEquals("tan", protocol.getCurrentFunction());
        assertEquals("0", protocol.process("0"));
        assertEquals("1", protocol.process("pi/4"));

        protocol.process("fun:cos");
        assertEquals("cos", protocol.getCurrentFunction());
    }

    @Test
    void deberiaAceptarElComandoSinImportarMayusculasNiEspacios() {
        assertEquals("Operacion cambiada a sin.", protocol.process("FUN: SIN"));
        assertEquals("sin", protocol.getCurrentFunction());
    }

    @Test
    void deberiaRechazarUnaOperacionNoSoportada() {
        String response = protocol.process("fun:log");

        assertTrue(response.startsWith("Error:"));
        // La operacion activa no debe cambiar ante un comando invalido.
        assertEquals("cos", protocol.getCurrentFunction());
    }

    @Test
    void deberiaAceptarNumerosDecimalesEnRadianes() {
        protocol.process("fun:sin");
        assertEquals("0.4794255386", protocol.process("0.5"));
    }

    @Test
    void deberiaReportarErrorConTextoNoNumerico() {
        assertTrue(protocol.process("hola").startsWith("Error:"));
        assertTrue(protocol.process("").startsWith("Error:"));
    }

    @Test
    void cadaConexionDeberiaTenerSuPropiaOperacionActiva() {
        protocol.process("fun:tan");
        MathFunctionProtocol otra = new MathFunctionProtocol();

        assertEquals("tan", protocol.getCurrentFunction());
        assertEquals("cos", otra.getCurrentFunction());
    }

    @Test
    void deberiaReconocerElMensajeDeSalida() {
        assertTrue(protocol.isQuit("Bye."));
        assertFalse(protocol.isQuit("fun:sin"));
    }
}
