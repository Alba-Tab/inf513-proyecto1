package bo.edu.uagrm.ficct.canal11.comandos;

import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.datos.Repositorio;
import bo.edu.uagrm.ficct.canal11.comandos.HTMLResponseBuilder.Tipo;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Catalogo;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.CommandSpec;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Respuesta a AYUDA (plantilla P1, sección 4.2.3): los comandos que puede usar el rol del
 * remitente, agrupados por caso de uso, con un ejemplo de cada uno.
 *
 * <p>Sale de las tablas {@code permiso} y {@code rol_permiso}: si el Director otorga o quita un
 * comando con INSRPM o DELRPM, la ayuda cambia sola.</p>
 */
public final class MenuAyuda {

    private static final String COMANDOS_DEL_ROL = """
            SELECT p.comando, p.descripcion, p.caso_uso
            FROM rol_permiso rp
            JOIN permiso p ON p.id = rp.permiso_id
            WHERE rp.rol_id = ?
            """;

    private final Repositorio repositorio;
    private final Catalogo catalogo;

    public MenuAyuda(Repositorio repositorio, Catalogo catalogo) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
    }

    /** Resultado con el menú; el mensaje dice cuántos comandos tiene el rol. */
    public Resultado armar(Connection c, Remitente r) throws SQLException {
        List<Fila> filas = new ArrayList<>(repositorio.consultar(c, COMANDOS_DEL_ROL, r.rolId()));
        filas.removeIf(f -> !catalogo.existe(f.texto("comando")));
        filas.sort(Comparator.comparingInt(f -> catalogo.posicion(f.texto("comando"))));

        String saludo = "Hola, " + r.nombreCompleto() + " (" + r.rol() + "). ";
        if (filas.isEmpty()) {
            return Resultado.informacion("Ayuda", saludo + "Su rol todavía no tiene comandos asignados. Contacte al Director del canal.");
        }
        Resultado resultado = Resultado.informacion("Ayuda", saludo + "Puede usar " + filas.size()
                + (filas.size() == 1 ? " comando" : " comandos") + ". Copie el ejemplo en el asunto del correo y cambie los datos.");

        Map<String, List<Fila>> porCasoDeUso = new LinkedHashMap<>();
        for (Fila f : filas) {
            porCasoDeUso.computeIfAbsent(f.texto("caso_uso"), k -> new ArrayList<>()).add(f);
        }
        boolean hayGuion = false;
        for (Map.Entry<String, List<Fila>> e : porCasoDeUso.entrySet()) {
            String nombre = Catalogo.CASOS_DE_USO.getOrDefault(e.getKey(), "");
            resultado.subtitulo(e.getKey() + (nombre.isEmpty() ? "" : " · " + nombre));
            List<List<String>> celdas = new ArrayList<>();
            for (Fila f : e.getValue()) {
                Optional<CommandSpec> spec = catalogo.buscar(f.texto("comando"));
                String descripcion = HTMLResponseBuilder.escapar(f.texto("descripcion"));
                if (spec.isPresent() && spec.get().pendiente()) {
                    descripcion += " <i>(próximamente)</i>";
                }
                hayGuion |= spec.isPresent() && spec.get().guionEnCuerpo();
                celdas.add(List.of(HTMLResponseBuilder.codigo(spec.map(CommandSpec::ejemplo).orElse(f.texto("comando"))), descripcion));
            }
            resultado.html(HTMLResponseBuilder.tablaHtml(List.of("Ejemplo", "Para qué sirve"), celdas));
        }
        if (hayGuion) {
            resultado.html(HTMLResponseBuilder.mensaje(Tipo.INFORMACION,
                    "En INSNOT y GUINOT el guion va en el cuerpo del correo, no en el asunto."));
        }
        resultado.parrafo("Un parámetro opcional se envía vacío (\"\"). En los comandos UPD, un parámetro vacío deja el dato como está. "
                + "En los LIS, \"*\" lista todo.");
        return resultado;
    }
}
