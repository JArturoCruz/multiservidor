        package mensaje ;
        import servidormulti.ServidorMulti;
        import servidormulti.UnCliente;

        import java.io.IOException;

        public class Mensaje {

            public static void procesar(String mensaje, UnCliente remitente) throws IOException {
                if (mensaje.startsWith("@")) {
                    enviarMensajePrivado(mensaje, remitente);
                } else {
                    difundirMensajePublico(mensaje, remitente);
                }
            }

            private static void enviarMensajePrivado(String mensaje, UnCliente remitente) throws IOException {
                String[] partes = mensaje.split(" ", 2);
                String destinatariosStr = partes[0].substring(1); // Quita la "@"
                String mensajePrivado = (partes.length > 1) ? partes[1] : "";

                if (mensajePrivado.isEmpty()) {
                    remitente.enviarMensaje("Sistema: No puedes enviar un mensaje privado vacío.");
                    return;
                }

                String[] destinatarios = destinatariosStr.split(",");
                String mensajeFormateado = "(Privado de " + remitente.getNombreCliente() + "): " + mensajePrivado;

                for (String dest : destinatarios) {
                    String nombreDestinatario = dest.trim();
                    UnCliente clienteDestino = ServidorMulti.clientes.get(nombreDestinatario);

                    if (clienteDestino != null) {
                        clienteDestino.enviarMensaje(mensajeFormateado);
                    } else {
                        remitente.enviarMensaje("Sistema: El usuario '" + nombreDestinatario + "' no está conectado o no existe.");
                    }
                }
                remitente.enviarMensaje("(Mensaje privado para " + destinatariosStr + "): " + mensajePrivado);
            }

            /**
             * Envía un mensaje a todos los clientes conectados.
             */
            private static void difundirMensajePublico(String mensaje, UnCliente remitente) throws IOException {
                String mensajeCompleto = remitente.getNombreCliente() + ": " + mensaje;
                for (UnCliente cliente : ServidorMulti.clientes.values()) {
                    cliente.enviarMensaje(mensajeCompleto);
                }
            }

            /**
             * Envía una notificación a todos los clientes (ej. conexiones/desconexiones).
             */
            public static void notificarATodos(String notificacion, UnCliente clienteExcluido) {
                System.out.println(notificacion);
                for (UnCliente cliente : ServidorMulti.clientes.values()) {
                    try {
                        // Se excluye a un cliente para no notificarle su propia acción.
                        if (cliente != clienteExcluido) {
                            cliente.enviarMensaje("Sistema: " + notificacion);
                        }
                    } catch (IOException e) {
                        // Ignorar si no se puede notificar a un cliente
                    }
                }
            }
        }