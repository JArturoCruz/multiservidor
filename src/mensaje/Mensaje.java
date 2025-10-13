package mensaje ;
import servidormulti.ServidorMulti;
import servidormulti.UnCliente;

import java.io.IOException;

public class Mensaje {

    private static String formatearMensajePrivado(String remitenteNombre, String contenido) {
        return "(Privado de " + remitenteNombre + "): " + contenido;
    }

    private static String formatearConfirmacionPrivada(String destinatarios, String contenido) {
        return "(Mensaje privado para " + destinatarios + "): " + contenido;
    }

    public static boolean procesar(String mensaje, UnCliente remitente) throws IOException {
        if (mensaje.startsWith("@")) {
            return enviarMensajePrivado(mensaje, remitente);
        } else {
            difundirMensajePublico(mensaje, remitente);
            return ServidorMulti.clientes.size() > 1;
        }
    }

    private static boolean validarMensajePrivado(String mensajePrivado, UnCliente remitente) throws IOException {
        if (mensajePrivado.isEmpty()) {
            remitente.enviarMensaje("Sistema: No puedes enviar un mensaje privado vacío.");
            return false;
        }
        return true;
    }

    private static void enviarAClientes(UnCliente remitente, String destinatariosStr, String mensajeParaDestinatarios) throws IOException {
        String[] destinatarios = destinatariosStr.split(",");

        for (String dest : destinatarios) {
            String nombreDestinatario = dest.trim();
            UnCliente clienteDestino = ServidorMulti.clientes.get(nombreDestinatario);

            if (clienteDestino != null) {
                clienteDestino.enviarMensaje(mensajeParaDestinatarios);
            } else {
                remitente.enviarMensaje("Sistema: El usuario '" + nombreDestinatario + "' no está conectado o no existe.");
            }
        }
    }

    private static boolean enviarMensajePrivado(String mensajeCompleto, UnCliente remitente) throws IOException {
        String[] partes = mensajeCompleto.split(" ", 2);
        String destinatariosStr = partes[0].substring(1);
        String mensajePrivado = (partes.length > 1) ? partes[1] : "";

        if (!validarMensajePrivado(mensajePrivado, remitente)) return false;

        String mensajeParaDestinatarios = formatearMensajePrivado(remitente.getNombreCliente(), mensajePrivado);

        enviarAClientes(remitente, destinatariosStr, mensajeParaDestinatarios);

        String mensajeConfirmacion = formatearConfirmacionPrivada(destinatariosStr, mensajePrivado);
        remitente.enviarMensaje(mensajeConfirmacion);

        return true;
    }

    private static void difundirMensajePublico(String mensaje, UnCliente remitente) throws IOException {
        String mensajeCompleto = remitente.getNombreCliente() + ": " + mensaje;
        for (UnCliente cliente : ServidorMulti.clientes.values()) {
            if (cliente != remitente) {
                cliente.enviarMensaje(mensajeCompleto);
            }
        }
    }

    private static void iterarYNotificar(String notificacion, UnCliente clienteExcluido) {
        for (UnCliente cliente : ServidorMulti.clientes.values()) {
            try {
                if (cliente != clienteExcluido) {
                    cliente.enviarMensaje("Sistema: " + notificacion);
                }
            } catch (IOException e) {
            }
        }
    }

    public static void notificarATodos(String notificacion, UnCliente clienteExcluido) {
        System.out.println(notificacion);
        iterarYNotificar(notificacion, clienteExcluido);
    }
}