/**
 * La tubería (pipes and filters): cada correo es un {@link bo.edu.uagrm.ficct.canal11.pipeline.Pedido}
 * que pasa por las etapas de {@code pipeline.etapas}, en el orden del flujo de atención (4.2.1).
 *
 * <ul>
 *   <li>{@code MailVerificationThread}: la fuente; saca los correos del buzón por POP3, de a uno.</li>
 *   <li>{@code Pipeline}: corre las etapas en orden; ninguna falla de un correo lo detiene.</li>
 *   <li>{@code SendEmailThread}: el sumidero; envía las respuestas por SMTP desde una cola.</li>
 *   <li>{@code CommandParser} y {@code Enmascarador}: leen el asunto y ocultan la contraseña.</li>
 * </ul>
 */
package bo.edu.uagrm.ficct.canal11.pipeline;
