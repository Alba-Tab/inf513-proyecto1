package bo.edu.uagrm.ficct.canal11.comandos;

import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.datos.Repositorio;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Catalogo;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Parametros;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Lo que tiene a mano la acción de un comando: quién lo envió, los parámetros ya validados, el
 * cuerpo del correo y la conexión de la transacción en curso.
 *
 * <p>Las consultas pasan por el {@link Repositorio}: siempre con {@code ?}, nunca concatenando
 * datos del correo.</p>
 */
public final class Contexto {

    private final Connection conexion;
    private final Repositorio repositorio;
    private final Remitente remitente;
    private final String codigo;
    private final Parametros parametros;
    private final String cuerpo;
    private final Clock reloj;
    private final Catalogo catalogo;

    public Contexto(Connection conexion, Repositorio repositorio, Remitente remitente, String codigo,
                    Parametros parametros, String cuerpo, Clock reloj, Catalogo catalogo) {
        this.conexion = conexion;
        this.repositorio = repositorio;
        this.remitente = remitente;
        this.codigo = codigo;
        this.parametros = parametros;
        this.cuerpo = cuerpo == null ? "" : cuerpo;
        this.reloj = reloj;
        this.catalogo = catalogo;
    }

    /** El catálogo de comandos. */
    public Catalogo catalogo() {
        return catalogo;
    }

    public Remitente remitente() {
        return remitente;
    }

    /** Código del comando que se está ejecutando (INSUSU, LISCOB…). */
    public String codigo() {
        return codigo;
    }

    public Parametros p() {
        return parametros;
    }

    /** Cuerpo del correo sin firma ni citas (el guion de INSNOT y GUINOT). */
    public String cuerpo() {
        return cuerpo;
    }

    public Clock reloj() {
        return reloj;
    }

    public LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    public Connection conexion() {
        return conexion;
    }

    public Repositorio repositorio() {
        return repositorio;
    }

    public List<Fila> consultar(String sql, Object... parametrosSql) throws SQLException {
        return repositorio.consultar(conexion, sql, parametrosSql);
    }

    /** La primera fila, si hay. */
    public Optional<Fila> uno(String sql, Object... parametrosSql) throws SQLException {
        return consultar(sql, parametrosSql).stream().findFirst();
    }

    /** La primera fila o «Error: el registro no existe». */
    public Fila requerir(String sql, Object... parametrosSql) throws SQLException, ErrorNegocio {
        return uno(sql, parametrosSql).orElseThrow(ErrorNegocio::noExiste);
    }

    public boolean existe(String sql, Object... parametrosSql) throws SQLException {
        return !consultar(sql, parametrosSql).isEmpty();
    }

    /** INSERT, UPDATE o DELETE: filas afectadas. */
    public int ejecutar(String sql, Object... parametrosSql) throws SQLException {
        return repositorio.ejecutar(conexion, sql, parametrosSql);
    }

    /** INSERT, UPDATE o DELETE con RETURNING: la primera fila o «Error: el registro no existe». */
    public Fila devolviendo(String sql, Object... parametrosSql) throws SQLException, ErrorNegocio {
        return repositorio.ejecutarDevolviendo(conexion, sql, parametrosSql).stream().findFirst()
                .orElseThrow(ErrorNegocio::noExiste);
    }
}
