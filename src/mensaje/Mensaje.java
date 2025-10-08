package mensaje ;
import servidormulti.ServidorMulti;
import servidormulti.UnCliente;

import java.io.IOException;

public class Mensaje {

    public static boolean procesar(String mensaje, UnCliente remitente) throws IOException {
        if (mensaje.startsWith("@")) {
            return enviarMensajePrivado(mensaje, remitente);
        } else {
            difundirMensajePublico(mensaje, remitente);
            return ServidorMulti.clientes.size() > 1;
        }
    }

    private static boolean enviarMensajePrivado(String mensaje, UnCliente remitente) throws IOException {
        String[] partes = mensaje.split(" ", 2);
        String destinatariosStr = partes[0].substring(1); // Quita la "@"
        String mensajePrivado = (partes.length > 1) ? partes[1] : "";
        boolean enviadoAAlguien = false;

        if (mensajePrivado.isEmpty()) {
            remitente.enviarMensaje("Sistema: No puedes enviar un mensaje privado vacío.");
            return false;
        }

        String[] destinatarios = destinatariosStr.split(",");
        String mensajeFormateado = "(Privado de " + remitente.getNombreCliente() + "): " + mensajePrivado;

        for (String dest : destinatarios) {
            String nombreDestinatario = dest.trim();
            UnCliente clienteDestino = ServidorMulti.clientes.get(nombreDestinatario);

            if (clienteDestino != null) {
                clienteDestino.enviarMensaje(mensajeFormateado);
                enviadoAAlguien = true;
            } else {
                remitente.enviarMensaje("Sistema: El usuario '" + nombreDestinatario + "' no está conectado o no existe.");
            }
        }

        remitente.enviarMensaje("(Mensaje privado para " + destinatariosStr + "): " + mensajePrivado);

        return enviadoAAlguien;
    }

    private static void difundirMensajePublico(String mensaje, UnCliente remitente) throws IOException {
        String mensajeCompleto = remitente.getNombreCliente() + ": " + mensaje;
        for (UnCliente cliente : ServidorMulti.clientes.values()) {
            if (cliente != remitente) {
                cliente.enviarMensaje(mensajeCompleto);
            }
        }
    }

    public static void notificarATodos(String notificacion, UnCliente clienteExcluido) {
        System.out.println(notificacion);
        for (UnCliente cliente : ServidorMulti.clientes.values()) {
            try {
                if (cliente != clienteExcluido) {
                    cliente.enviarMensaje("Sistema: " + notificacion);
                }
            } catch (IOException e) {
            }
        }
    }
}