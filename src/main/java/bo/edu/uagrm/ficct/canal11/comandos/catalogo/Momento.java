package bo.edu.uagrm.ficct.canal11.comandos.catalogo;

import java.time.LocalDateTime;

/**
 * Un parámetro que puede ser una fecha sola ({@code 2026-10-14}) o una fecha con hora
 * ({@code 2026-10-14 09:00}), como el inicio y el fin de una ausencia (INSAUS).
 *
 * @param valor     la fecha y hora; si llegó solo la fecha, a las 00:00
 * @param soloFecha {@code true} si llegó solo la fecha (un día completo)
 */
public record Momento(LocalDateTime valor, boolean soloFecha) {

    /** Como inicio de un rango: la fecha sola empieza a las 00:00. */
    public LocalDateTime comoInicio() {
        return valor;
    }

    /**
     * Como fin de un rango con fin exclusivo (R11): la fecha sola incluye ese día completo, así que
     * termina a las 00:00 del día siguiente.
     */
    public LocalDateTime comoFin() {
        return soloFecha ? valor.plusDays(1) : valor;
    }
}
