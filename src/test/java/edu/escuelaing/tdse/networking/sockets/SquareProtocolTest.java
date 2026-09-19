package edu.escuelaing.tdse.networking.sockets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Ejercicio 4.3.1: el servidor devuelve el cuadrado del numero recibido. */
class SquareProtocolTest {

    private SquareProtocol protocol;

    @BeforeEach
    void setUp() {
        protocol = new SquareProtocol();
    }

    @Test
    void deberiaCalcularElCuadradoDeUnEntero() {
        assertEquals("25", protocol.process("5"));
        assertEquals("144", protocol.process("12"));
        assertEquals("0", protocol.process("0"));
    }

    @Test
    void deberiaCalcularElCuadradoDeUnNegativo() {
        assertEquals("9", protocol.process("-3"));
    }

    @Test
    void deberiaCalcularElCuadradoDeUnDecimal() {
        assertEquals("6.25", protocol.process("2.5"));
    }

    @Test
    void deberiaIgnorarEspaciosAlrededorDelNumero() {
        assertEquals("49", protocol.process("  7  "));
    }

    @Test
    void deberiaReportarErrorConTextoNoNumerico() {
        assertTrue(protocol.process("hola").startsWith("Error:"));
        assertTrue(protocol.process("").startsWith("Error:"));
    }

    @Test
    void deberiaReconocerElMensajeDeSalida() {
        assertTrue(protocol.isQuit("Bye."));
        assertTrue(protocol.isQuit("  Bye.  "));
        assertTrue(!protocol.isQuit("5"));
    }
}
