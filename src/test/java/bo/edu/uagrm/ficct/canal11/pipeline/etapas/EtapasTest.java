package bo.edu.uagrm.ficct.canal11.pipeline.etapas;

import bo.edu.uagrm.ficct.canal11.correo.CorreoSaliente;
import bo.edu.uagrm.ficct.canal11.pipeline.Etapa;
import bo.edu.uagrm.ficct.canal11.pipeline.Pedido;
import bo.edu.uagrm.ficct.canal11.pipeline.Pipeline;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cómo se comporta la tubería, sin base de datos: qué etapas corren y qué respuesta queda. */
class EtapasTest {

    private static final String SISTEMA = "grupo14sa@tecnoweb.org.bo";

    private final List<String> corridas = new ArrayList<>();
    private final List<CorreoSaliente> enviados = new ArrayList<>();

    private Etapa anotar(String nombre) {
        return p -> corridas.add(nombre);
    }

    private Pipeline tuberia(Etapa... medio) {
        List<Etapa> e = new ArrayList<>(List.of(new Decodificar(), new Filtrar(SISTEMA, 2_000)));
        e.addAll(List.of(medio));
        e.add(new Responder(SISTEMA, enviados::add));
        return new Pipeline(e);
    }

    private static Pedido correo(String de, String asunto, String... cabecerasExtra) {
        String extra = cabecerasExtra.length == 0 ? "" : String.join("\r\n", cabecerasExtra) + "\r\n";
        String crudo = "From: " + de + "\r\nTo: " + SISTEMA + "\r\nSubject: " + asunto + "\r\n" + extra
                + "Content-Type: text/plain; charset=UTF-8\r\n\r\nhola\r\n";
        byte[] b = crudo.getBytes(StandardCharsets.UTF_8);
        return new Pedido(b, false, b.length);
    }

    @Test
    void unCorreoNormalPasaPorTodasLasEtapasYSeResponde() {
        Pedido p = correo("carla@gmail.com", "LISCOB");
        tuberia(anotar("a"), q -> q.responder(true, "<p>ok</p>", "ok", List.of()), anotar("despues")).procesar(p);
        assertEquals(List.of("a"), corridas, "después de responder se saltan las etapas que no corren siempre");
        assertEquals(1, enviados.size());
        assertEquals("Re: LISCOB", enviados.get(0).asunto());
    }

    @Test
    void unaEtapaQueSiempreCorreSeEjecutaAunqueHayaRespuesta() {
        Etapa siempre = new Etapa() {
            @Override
            public void procesar(Pedido p) {
                corridas.add("siempre");
            }

            @Override
            public boolean siempre() {
                return true;
            }
        };
        tuberia(q -> q.error("<p>x</p>", "x"), anotar("salteada"), siempre).procesar(correo("carla@gmail.com", "X"));
        assertEquals(List.of("siempre"), corridas);
        assertEquals(1, enviados.size());
    }

    @Test
    void losCorreosAutomaticosYLosPropiosSeDescartanSinResponder() {
        tuberia(anotar("comando")).procesar(correo("MAILER-DAEMON@x.com", "Undelivered", "Auto-Submitted: auto-replied"));
        tuberia(anotar("comando")).procesar(correo(SISTEMA, "Re: LISCOB"));
        assertTrue(corridas.isEmpty());
        assertTrue(enviados.isEmpty());
    }

    @Test
    void unCorreoDemasiadoGrandeSeRespondeSinEjecutarElComando() {
        Pedido grande = new Pedido(("From: carla@gmail.com\r\nSubject: LISCOB\r\n\r\n").getBytes(StandardCharsets.UTF_8), true, 5_000);
        tuberia(anotar("comando")).procesar(grande);
        assertTrue(corridas.isEmpty());
        assertFalse(grande.exito());
        assertTrue(grande.resumen().contains("no acepta adjuntos"), grande.resumen());
        assertEquals(1, enviados.size());
    }

    @Test
    void unaEtapaQueFallaDejaUnaRespuestaDeErrorYSiguenLasQueCorrenSiempre() {
        Pedido p = correo("carla@gmail.com", "LISCOB");
        tuberia(q -> {
            throw new IllegalStateException("bug");
        }, anotar("salteada")).procesar(p);
        assertTrue(corridas.isEmpty());
        assertEquals(Pipeline.ERROR_GENERAL, p.resumen());
        assertEquals(1, enviados.size());
    }

    @Test
    void elAsuntoDeLaRespuestaVaEnmascarado() {
        Pedido p = correo("director@gmail.com",
                "INSUSU[\"Ana\",\"Paz\",\"9100001\",\"ana@x.com\",\"\",\"clave-secreta\",\"3\"]");
        tuberia(q -> q.responder(true, "<p>ok</p>", "ok", List.of())).procesar(p);
        assertFalse(enviados.get(0).asunto().contains("clave-secreta"), enviados.get(0).asunto());
    }
}
