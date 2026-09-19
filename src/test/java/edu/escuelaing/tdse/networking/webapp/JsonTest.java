package edu.escuelaing.tdse.networking.webapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/** El escape del JSON es la defensa contra un valor del cliente que rompa el documento. */
class JsonTest {

    @Test
    void deberiaConservarElOrdenDeLosCampos() {
        String json = Json.object().put("a", 1L).put("b", "dos").build();

        assertEquals("{\"a\":1,\"b\":\"dos\"}", json);
    }

    @Test
    void deberiaEscaparComillasYBarras() {
        String json = Json.object().put("name", "a\"b\\c").build();

        assertEquals("{\"name\":\"a\\\"b\\\\c\"}", json);
    }

    @Test
    void deberiaEscaparSaltosDeLineaYTabulaciones() {
        String json = Json.object().put("name", "linea1\nlinea2\ttab").build();

        assertEquals("{\"name\":\"linea1\\nlinea2\\ttab\"}", json);
    }

    @Test
    void deberiaEscaparCaracteresDeControl() {
        String json = Json.object().put("name", "fin" + (char) 0x00 + (char) 0x1F).build();

        assertEquals("{\"name\":\"fin\\u0000\\u001f\"}", json);
    }

    /** Un intento de inyectar una etiqueta no puede terminar como marcado en la interfaz. */
    @Test
    void deberiaDejarUnJsonValidoConUnNombreHostil() {
        String hostil = "</script><img src=x onerror=\"alert(1)\">";

        String json = Json.object().put("name", hostil).build();

        // La comilla del atributo queda escapada, asi que el documento sigue teniendo un solo campo.
        assertEquals(1, contar(json, "\":\""));
        assertFalse(json.contains("onerror=\"alert"));
    }

    @Test
    void deberiaFormatearNumerosEnterosSinDecimales() {
        assertEquals("25", Json.number(25.0));
        assertEquals("-9", Json.number(-9.0));
    }

    @Test
    void deberiaEvitarLaNotacionCientifica() {
        assertEquals("0.00000625", Json.number(0.00000625));
    }

    @Test
    void deberiaReportarNullParaValoresNoFinitos() {
        assertEquals("null", Json.number(Double.POSITIVE_INFINITY));
        assertEquals("null", Json.number(Double.NaN));
    }

    private static int contar(String texto, String fragmento) {
        int total = 0;
        int desde = 0;
        int encontrado;
        while ((encontrado = texto.indexOf(fragmento, desde)) >= 0) {
            total++;
            desde = encontrado + fragmento.length();
        }
        return total;
    }
}
