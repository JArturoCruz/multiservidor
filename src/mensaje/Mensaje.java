package mensaje ;
import servidormulti.ServidorMulti;
import servidormulti.UnCliente;
import bd.BDusuarios;

import java.io.IOException;

public class Mensaje {

    private static String formatearMensajePrivado(String remitenteNombre, String contenido) {
        return "(Privado de " + remitenteNombre + "): " + contenido;
    }

    private static String formatearConfirmacionPrivada(String destinatarios, String contenido) {
        return "(Mensaje privado para " + destinatarios + "): " + contenido;
    }

    public static boolean procesar(String mensaje, UnCliente remitente, ServidorMulti servidor) throws IOException {
        if (mensaje.startsWith("@")) {
            return enviarMensajePrivado(mensaje, remitente, servidor);
        } else {
            difundirMensajePublico(mensaje, remitente, servidor);
            return servidor.getTodosLosClientes().size() > 1;
        }
    }
    private static boolean validarMensajePrivado(String mensajePrivado, UnCliente remitente) throws IOException {
        if (mensajePrivado.isEmpty()) {
            remitente.enviarMensaje("Sistema: No puedes enviar un mensaje privado vacío.");
            return false;
        }
        return true;
    }

    private static void enviarAClientes(UnCliente remitente, String destinatariosStr, String mensajeParaDestinatarios, ServidorMulti servidor) throws IOException {
        String remitenteNombre = remitente.getNombreCliente();
        String[] destinatarios = destinatariosStr.split(",");
        StringBuilder destinatariosEnviados = new StringBuilder();
        boolean alMenosUnoEnviado = false;

        for (String dest : destinatarios) {
            String nombreDestinatario = dest.trim();
            UnCliente clienteDestino = servidor.getCliente(nombreDestinatario);

            if (clienteDestino != null) {
                boolean bloqueadoPorDestino = BDusuarios.estaBloqueado(nombreDestinatario, remitenteNombre);
                boolean bloqueadoPorRemitente = BDusuarios.estaBloqueado(remitenteNombre, nombreDestinatario);

                if (bloqueadoPorDestino || bloqueadoPorRemitente) {
                    String razon = bloqueadoPorDestino ?
                            "Te ha bloqueado." :
                            "Le has bloqueado.";

                    remitente.enviarMensaje("Sistema: Error al enviar mensaje privado a '" + nombreDestinatario + "'. " + razon);
                    continue;
                }
                clienteDestino.enviarMensaje(mensajeParaDestinatarios);
                if (destinatariosEnviados.length() > 0) destinatariosEnviados.append(", ");
                destinatariosEnviados.append(nombreDestinatario);
                alMenosUnoEnviado = true;
            } else {
                remitente.enviarMensaje("Sistema: El usuario '" + nombreDestinatario + "' no está conectado o no existe.");
            }
        }

        if (alMenosUnoEnviado) {
            String contenidoMensaje = mensajeParaDestinatarios.substring(mensajeParaDestinatarios.indexOf("): ") + 3);
            String mensajeConfirmacion = formatearConfirmacionPrivada(destinatariosEnviados.toString(), contenidoMensaje);
            remitente.enviarMensaje(mensajeConfirmacion);
        }
    }

    private static boolean enviarMensajePrivado(String mensajeCompleto, UnCliente remitente, ServidorMulti servidor) throws IOException {
        String[] partes = mensajeCompleto.split(" ", 2);
        String destinatariosStr = partes[0].substring(1);
        String mensajePrivado = (partes.length > 1) ? partes[1] : "";

        if (!validarMensajePrivado(mensajePrivado, remitente)) return false;
        String mensajeParaDestinatarios = formatearMensajePrivado(remitente.getNombreCliente(), mensajePrivado);
        enviarAClientes(remitente, destinatariosStr, mensajeParaDestinatarios, servidor);
        return true;
    }

    private static void difundirMensajePublico(String mensaje, UnCliente remitente, ServidorMulti servidor) throws IOException {
        String remitenteNombre = remitente.getNombreCliente();
        String mensajeCompleto = remitenteNombre + ": " + mensaje;
        remitente.enviarMensaje("(Mensaje público enviado)");

        for (UnCliente cliente : servidor.getTodosLosClientes()) {
            if (cliente != remitente) {
                String clienteNombre = cliente.getNombreCliente();
                boolean bloqueadoPorReceptor = BDusuarios.estaBloqueado(clienteNombre, remitenteNombre);
                boolean bloqueadoPorRemitente = BDusuarios.estaBloqueado(remitenteNombre, clienteNombre);
                if (bloqueadoPorReceptor || bloqueadoPorRemitente) {
                    continue;
                }
                cliente.enviarMensaje(mensajeCompleto);
            }
        }
    }

    private static void iterarYNotificar(String notificacion, UnCliente clienteExcluido, ServidorMulti servidor) {
        for (UnCliente cliente : servidor.getTodosLosClientes()) {
            try {
                if (cliente != clienteExcluido) {
                    cliente.enviarMensaje("Sistema: " + notificacion);
                }
            } catch (IOException e) {
            }
        }
    }

    public static void notificarATodos(String notificacion, UnCliente clienteExcluido, ServidorMulti servidor) {
        System.out.println(notificacion);
        iterarYNotificar(notificacion, clienteExcluido, servidor);
    }
}