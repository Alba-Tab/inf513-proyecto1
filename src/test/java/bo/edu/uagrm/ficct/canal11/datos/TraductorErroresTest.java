package bo.edu.uagrm.ficct.canal11.datos;

import bo.edu.uagrm.ficct.canal11.datos.TraductorErrores.Operacion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.postgresql.util.ServerErrorMessage;

import java.net.SocketTimeoutException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas sin base de datos: errores armados con el mismo formato que envía PostgreSQL. */
class TraductorErroresTest {

    private static final String TEXTO_CRUDO = "duplicate key value violates unique constraint";

    /** Arma un error de PostgreSQL: código SQLSTATE, restricción y columna (las dos últimas opcionales). */
    private static PSQLException error(String sqlState, String restriccion, String columna) {
        StringBuilder sb = new StringBuilder()
                .append("SERROR\0")
                .append("C").append(sqlState).append('\0')
                .append("M").append(TEXTO_CRUDO).append('\0');
        if (restriccion != null) {
            sb.append("n").append(restriccion).append('\0');
        }
        if (columna != null) {
            sb.append("c").append(columna).append('\0');
        }
        return new PSQLException(new ServerErrorMessage(sb.toString()));
    }

    @Test
    void traduceUnaRestriccionUnica() {
        assertEquals("Error: el correo ya está registrado",
                TraductorErrores.traducir(error("23505", "usuario_correo_key", null), Operacion.REGISTRAR));
    }

    @Test
    void traduceUnIndiceUnicoParcial() {
        assertEquals("Error: el recurso ya está entregado y no fue devuelto",
                TraductorErrores.traducir(error("23505", "ux_prestamo_abierto", null), Operacion.REGISTRAR));
    }

    @Test
    void laClaveForaneaDependeDeLaOperacion() {
        PSQLException e = error("23503", "usuario_rol_id_fkey", null);
        assertEquals("Error: el rol indicado no existe", TraductorErrores.traducir(e, Operacion.REGISTRAR));
        assertEquals("Error: el rol indicado no existe", TraductorErrores.traducir(e, Operacion.MODIFICAR));
        assertEquals("Error: el rol tiene usuarios asignados", TraductorErrores.traducir(e, Operacion.ELIMINAR));
    }

    @Test
    void unaClaveForaneaSinMensajeDeUsoDaUnMensajeGeneralAlEliminar() {
        assertEquals("Error: el registro tiene datos asociados y no se puede eliminar",
                TraductorErrores.traducir(error("23503", "nota_rotulo_nota_id_fkey", null), Operacion.ELIMINAR));
    }

    @Test
    void laClavePrimariaDuplicadaEsUnRegistroRepetido() {
        assertEquals("Error: el registro ya existe",
                TraductorErrores.traducir(error("23505", "rol_pkey", null), Operacion.REGISTRAR));
    }

    @Test
    void unaRestriccionDesconocidaUsaElCodigoSqlstate() {
        assertEquals("Error: el registro ya existe",
                TraductorErrores.traducir(error("23505", "restriccion_nueva_key", null), Operacion.REGISTRAR));
        assertEquals("Error: un dato no cumple las reglas del sistema",
                TraductorErrores.traducir(error("23514", "check_nuevo", null), Operacion.REGISTRAR));
    }

    @Test
    void unDatoObligatorioFaltanteNombraLaColumna() {
        assertEquals("Error: falta el dato «fecha hora inicio»",
                TraductorErrores.traducir(error("23502", null, "fecha_hora_inicio"), Operacion.REGISTRAR));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "22001 | Error: un texto supera el largo permitido",
            "22003 | Error: un número está fuera del rango permitido",
            "22007 | Error: una fecha u hora no es válida",
            "22008 | Error: una fecha u hora no es válida",
            "22P02 | Error: un dato tiene un formato inválido",
            "57014 | Error: la operación tardó demasiado; intente de nuevo",
            "40P01 | Error: hubo un conflicto con otra operación; intente de nuevo",
            "08006 | Error: el sistema no puede acceder a la base de datos en este momento; intente más tarde",
            "57P01 | Error: el sistema no puede acceder a la base de datos en este momento; intente más tarde",
            "XX000 | Error: no se pudo completar la operación"
    })
    void traducePorCodigoSqlstate(String sqlState, String esperado) {
        assertEquals(esperado, TraductorErrores.traducir(error(sqlState, null, null), Operacion.CONSULTAR));
    }

    @Test
    void unCorteDeRedEsFaltaDeAccesoALaBase() {
        PSQLException e = new PSQLException("I/O error", PSQLState.CONNECTION_FAILURE, new SocketTimeoutException("Read timed out"));
        assertEquals("Error: el sistema no puede acceder a la base de datos en este momento; intente más tarde",
                TraductorErrores.traducir(e, Operacion.CONSULTAR));
        assertEquals("Error: el sistema no puede acceder a la base de datos en este momento; intente más tarde",
                TraductorErrores.traducir(new SQLException("sin estado", (String) null, new SocketTimeoutException()), Operacion.CONSULTAR));
    }

    @Test
    void nuncaDevuelveElTextoCrudoDePostgres() {
        for (String estado : new String[]{"23505", "23503", "23514", "XX000"}) {
            String mensaje = TraductorErrores.traducir(error(estado, "lo_que_sea", null), Operacion.REGISTRAR);
            assertTrue(mensaje.startsWith("Error: "), mensaje);
            assertFalse(mensaje.contains(TEXTO_CRUDO), mensaje);
            assertFalse(mensaje.contains("lo_que_sea"), mensaje);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "INSUSU, REGISTRAR", "DUPCOB, REGISTRAR", "DELREC, ELIMINAR", "LISCOB, CONSULTAR", "HOJCOB, CONSULTAR",
            "REPCOB, CONSULTAR", "ANAPER, CONSULTAR", "UPDNOT, MODIFICAR", "APRNOT, MODIFICAR", "RETCOB, MODIFICAR",
            "inscob, REGISTRAR"
    })
    void laOperacionSaleDelCodigoDelComando(String codigo, Operacion esperada) {
        assertEquals(esperada, Operacion.delComando(codigo));
    }
}
