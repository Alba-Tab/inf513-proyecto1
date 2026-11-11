package bo.edu.uagrm.ficct.canal11.comandos.catalogo;

import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * La ficha de un comando del Anexo A: código, caso de uso, descripción, parámetros con su tipo,
 * ejemplo y la acción que lo ejecuta (decisión D11).
 *
 * <p>Con la ficha, {@code CommandProcessor} valida la cantidad y el tipo de los parámetros antes de
 * ejecutar nada, y AYUDA muestra el ejemplo de cada comando.</p>
 */
public final class CommandSpec {

    private final String codigo;
    private final String casoUso;
    private final String descripcion;
    private final List<ParamSpec> parametros;
    private final String ejemplo;
    private final boolean guionEnCuerpo;
    private final Accion accion;

    private CommandSpec(Builder b) {
        this.codigo = b.codigo;
        this.casoUso = b.casoUso;
        this.descripcion = b.descripcion;
        this.parametros = List.copyOf(b.parametros);
        this.ejemplo = Objects.requireNonNull(b.ejemplo, "Falta el ejemplo de " + b.codigo);
        this.guionEnCuerpo = b.guionEnCuerpo;
        this.accion = b.accion;
    }

    /** {@code de("CU-01", "INSUSU", "Registrar usuario")}. */
    public static Builder de(String casoUso, String codigo, String descripcion) {
        return new Builder(casoUso, codigo, descripcion);
    }

    public String codigo() {
        return codigo;
    }

    public String casoUso() {
        return casoUso;
    }

    public String descripcion() {
        return descripcion;
    }

    public List<ParamSpec> parametros() {
        return parametros;
    }

    public String ejemplo() {
        return ejemplo;
    }

    /** {@code true} si el comando toma el guion del cuerpo del correo (INSNOT, GUINOT). */
    public boolean guionEnCuerpo() {
        return guionEnCuerpo;
    }

    public Accion accion() {
        return accion;
    }

    /** {@code true} si el comando todavía no tiene su acción. */
    public boolean pendiente() {
        return accion == Accion.PENDIENTE;
    }

    /** Las tres primeras letras: LIS, INS, UPD… */
    public String operacion() {
        return codigo.substring(0, 3);
    }

    /** Cantidad mínima de parámetros (los omitibles van al final). */
    public int minimo() {
        return (int) parametros.stream().filter(p -> !p.omitible()).count();
    }

    public int maximo() {
        return parametros.size();
    }

    /** El formato con los nombres de los parámetros: {@code DELUSU["id"]}. */
    public String formato() {
        return codigo + parametros.stream().map(p -> "\"" + p.nombre() + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * Valida la cantidad y el tipo de cada parámetro y los convierte.
     *
     * @throws ErrorNegocio con el primer problema encontrado («Error: se esperaban 2 parámetros…»)
     */
    public Parametros validar(List<String> valores) throws ErrorNegocio {
        int n = valores.size();
        if (n < minimo() || n > maximo()) {
            throw ErrorNegocio.de(cantidadEsperada() + " y " + llegaron(n));
        }
        Map<String, Object> convertidos = new LinkedHashMap<>();
        Map<String, String> crudos = new LinkedHashMap<>();
        for (int i = 0; i < parametros.size(); i++) {
            ParamSpec p = parametros.get(i);
            String valor = i < n ? valores.get(i).strip() : "";
            crudos.put(p.nombre(), valor);
            if (valor.isEmpty()) {
                if (!p.opcional()) {
                    throw ErrorNegocio.de("falta «" + p.nombre() + "»: es obligatorio");
                }
                convertidos.put(p.nombre(), null);
            } else {
                convertidos.put(p.nombre(), p.tipo().convertir(valor, p.nombre()));
            }
        }
        return new Parametros(convertidos, crudos);
    }

    private String cantidadEsperada() {
        String nombres = parametros.stream().map(ParamSpec::nombre).collect(Collectors.joining(", ", " (", ")"));
        if (minimo() == maximo()) {
            return maximo() == 1 ? "se esperaba 1 parámetro" + nombres : "se esperaban " + maximo() + " parámetros" + nombres;
        }
        return "se esperaban entre " + minimo() + " y " + maximo() + " parámetros" + nombres;
    }

    private static String llegaron(int n) {
        if (n == 0) {
            return "no llegó ninguno";
        }
        return n == 1 ? "llegó 1" : "llegaron " + n;
    }

    @Override
    public String toString() {
        return formato();
    }

    /** Arma una ficha. Los parámetros se agregan en el orden del Anexo A. */
    public static final class Builder {
        private final String casoUso;
        private final String codigo;
        private final String descripcion;
        private final List<ParamSpec> parametros = new ArrayList<>();
        private String ejemplo;
        private boolean guionEnCuerpo;
        private Accion accion = Accion.PENDIENTE;

        private Builder(String casoUso, String codigo, String descripcion) {
            if (!codigo.matches("^[A-Z]{6}$")) {
                throw new IllegalArgumentException("Código inválido: " + codigo);
            }
            this.casoUso = casoUso;
            this.codigo = codigo;
            this.descripcion = descripcion;
        }

        /** Parámetro obligatorio. */
        public Builder param(String nombre, Tipo tipo) {
            return agregar(new ParamSpec(nombre, tipo, false, false));
        }

        /** Parámetro que se puede enviar vacío ({@code ""}). */
        public Builder opcional(String nombre, Tipo tipo) {
            return agregar(new ParamSpec(nombre, tipo, true, false));
        }

        /** Parámetro opcional que además se puede no enviar; va al final. */
        public Builder omitible(String nombre, Tipo tipo) {
            return agregar(new ParamSpec(nombre, tipo, true, true));
        }

        private Builder agregar(ParamSpec p) {
            if (!parametros.isEmpty() && parametros.get(parametros.size() - 1).omitible() && !p.omitible()) {
                throw new IllegalStateException(codigo + ": los parámetros omitibles van al final");
            }
            parametros.add(p);
            return this;
        }

        public Builder ejemplo(String ejemplo) {
            this.ejemplo = ejemplo;
            return this;
        }

        public Builder guionEnCuerpo() {
            this.guionEnCuerpo = true;
            return this;
        }

        public Builder accion(Accion accion) {
            this.accion = Objects.requireNonNull(accion);
            return this;
        }

        public CommandSpec build() {
            return new CommandSpec(this);
        }
    }
}
