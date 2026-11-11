package bo.edu.uagrm.ficct.canal11.comandos.catalogo;

import bo.edu.uagrm.ficct.canal11.comandos.cu01.ComandosUsuarios;
import bo.edu.uagrm.ficct.canal11.comandos.cu02.ComandosPersonal;
import bo.edu.uagrm.ficct.canal11.comandos.cu03.ComandosRecursos;
import bo.edu.uagrm.ficct.canal11.comandos.cu04.ComandosCoberturas;
import bo.edu.uagrm.ficct.canal11.comandos.cu05.ComandosNotas;
import bo.edu.uagrm.ficct.canal11.comandos.cu06.ComandosEscaleta;
import bo.edu.uagrm.ficct.canal11.comandos.cu06.ComandosProgramacion;
import bo.edu.uagrm.ficct.canal11.comandos.cu07.ComandosAnalitica;
import bo.edu.uagrm.ficct.canal11.comandos.cu08.ComandosReportes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Los 58 comandos esenciales del sistema (Anexo A), en el orden del documento. AYUDA no está aquí: no es un
 * comando de una tabla ni necesita permiso.
 *
 * <p>Que un comando exista en el catálogo no da permiso para ejecutarlo: eso lo deciden las tablas
 * {@code permiso} y {@code rol_permiso} (R1), que el Director administra por correo.</p>
 */
public final class Catalogo {

    /** Nombre corto de cada caso de uso, para agrupar AYUDA. */
    public static final Map<String, String> CASOS_DE_USO = Map.of(
            "CU-01", "Usuarios, roles y permisos",
            "CU-02", "Personal, turnos y disponibilidad",
            "CU-03", "Equipos y unidades móviles",
            "CU-04", "Coberturas",
            "CU-05", "Notas, material y aprobación",
            "CU-06", "Programación y escaleta",
            "CU-07", "Analítica periodística",
            "CU-08", "Reportes y bitácora");

    private final Map<String, CommandSpec> comandos;
    private final Map<String, Integer> posiciones = new LinkedHashMap<>();

    public Catalogo(Collection<CommandSpec> specs) {
        Map<String, CommandSpec> m = new LinkedHashMap<>();
        for (CommandSpec s : specs) {
            if (m.put(s.codigo(), s) != null) {
                throw new IllegalStateException("Comando repetido en el catálogo: " + s.codigo());
            }
            posiciones.put(s.codigo(), posiciones.size());
        }
        this.comandos = Collections.unmodifiableMap(m);
    }

    /** El catálogo completo de la aplicación. */
    public static Catalogo completo() {
        List<CommandSpec> todos = new ArrayList<>();
        todos.addAll(ComandosUsuarios.comandos());
        todos.addAll(ComandosPersonal.comandos());
        todos.addAll(ComandosRecursos.comandos());
        todos.addAll(ComandosCoberturas.comandos());
        todos.addAll(ComandosNotas.comandos());
        todos.addAll(ComandosProgramacion.comandos());
        todos.addAll(ComandosEscaleta.comandos());
        todos.addAll(ComandosAnalitica.comandos());
        todos.addAll(ComandosReportes.comandos());
        return new Catalogo(todos);
    }

    public Optional<CommandSpec> buscar(String codigo) {
        return Optional.ofNullable(codigo == null ? null : comandos.get(codigo));
    }

    public boolean existe(String codigo) {
        return codigo != null && comandos.containsKey(codigo);
    }

    /** Posición en el orden del Anexo A (para ordenar AYUDA); los desconocidos van al final. */
    public int posicion(String codigo) {
        return posiciones.getOrDefault(codigo, Integer.MAX_VALUE);
    }

    public Collection<CommandSpec> todos() {
        return comandos.values();
    }

    public int cantidad() {
        return comandos.size();
    }
}
