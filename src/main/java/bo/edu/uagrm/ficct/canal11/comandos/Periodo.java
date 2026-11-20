package bo.edu.uagrm.ficct.canal11.comandos;

import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Parametros;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * El período de un reporte o un indicador (CU-07 y CU-08): {@code fecha_desde} y
 * {@code fecha_hasta}, ambas incluidas.
 *
 * <p>Si la fecha final es anterior a la inicial, o el período pasa de {@link #MAXIMO_DIAS} días,
 * responde «Error: período inválido» (flujo alternativo A1). Sin datos, cada comando responde
 * {@link #SIN_DATOS} (A2).</p>
 */
public record Periodo(LocalDate desde, LocalDate hasta) {

    /** Decisión D30: un correo no pide más de un año de datos. */
    public static final int MAXIMO_DIAS = 366;
    public static final String SIN_DATOS = "No hay datos para el período indicado";

    /** El período de los parámetros {@code fecha_desde} y {@code fecha_hasta}, validado. */
    public static Periodo de(Parametros p) throws ErrorNegocio {
        LocalDate desde = p.fecha("fecha_desde");
        LocalDate hasta = p.fecha("fecha_hasta");
        if (hasta.isBefore(desde)) {
            throw ErrorNegocio.de("período inválido: la fecha final es anterior a la inicial");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS) {
            throw ErrorNegocio.de("período inválido: pida hasta " + MAXIMO_DIAS + " días");
        }
        return new Periodo(desde, hasta);
    }

    /** «del 2026-10-01 al 2026-10-31» (o «del 2026-10-12» si es un solo día). */
    public String texto() {
        return desde.equals(hasta) ? "del " + desde : "del " + desde + " al " + hasta;
    }
}
