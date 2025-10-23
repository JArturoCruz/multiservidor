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

    public static void enviarMensajePrivadoEntreJugadores(String mensaje, UnCliente remitente, String nombreDestino, ServidorMulti servidor) throws IOException {
        UnCliente clienteDestino = servidor.getCliente(nombreDestino);

        if (clienteDestino != null) {
            String mensajeParaDestinatarios = formatearMensajePrivado(remitente.getNombreCliente(), mensaje);
            clienteDestino.enviarMensaje(mensajeParaDestinatarios);

            String mensajeConfirmacion = formatearConfirmacionPrivada(nombreDestino, mensaje);
            remitente.enviarMensaje(mensajeConfirmacion);
        } else {
            remitente.enviarMensaje("Sistema: Error interno. El oponente (" + nombreDestino + ") no está conectado.");
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
        String[] destinatarios = destinatariosStr.split(",");
        StringBuilder destinatariosEnviados = new StringBuilder();
        boolean alMenosUnoEnviado = false;

        for (String dest : destinatarios) {
            String nombreDestinatario = dest.trim();
            alMenosUnoEnviado = procesarDestinatarioPrivado(remitente, nombreDestinatario, mensajeParaDestinatarios, servidor, destinatariosEnviados) || alMenosUnoEnviado;
        }

        if (alMenosUnoEnviado) {
            String contenidoMensaje = mensajeParaDestinatarios.substring(mensajeParaDestinatarios.indexOf("): ") + 3);
            String mensajeConfirmacion = formatearConfirmacionPrivada(destinatariosEnviados.toString(), contenidoMensaje);
            remitente.enviarMensaje(mensajeConfirmacion);
        }
    }

    private static boolean procesarDestinatarioPrivado(UnCliente remitente, String nombreDestinatario, String mensajeParaDestinatarios, ServidorMulti servidor, StringBuilder destinatariosEnviados) throws IOException {
        UnCliente clienteDestino = servidor.getCliente(nombreDestinatario);
        String remitenteNombre = remitente.getNombreCliente();

        if (clienteDestino == null) {
            remitente.enviarMensaje("Sistema: El usuario '" + nombreDestinatario + "' no está conectado o no existe.");
            return false;
        }

        if (esBloqueoBidireccional(nombreDestinatario, remitenteNombre, remitente)) {
            return false;
        }

        clienteDestino.enviarMensaje(mensajeParaDestinatarios);
        if (destinatariosEnviados.length() > 0) destinatariosEnviados.append(", ");
        destinatariosEnviados.append(nombreDestinatario);
        return true;
    }

    private static boolean esBloqueoBidireccional(String nombre1, String nombre2, UnCliente notificador) throws IOException {
        boolean bloqueadoPorDestino = BDusuarios.estaBloqueado(nombre1, nombre2);
        boolean bloqueadoPorRemitente = BDusuarios.estaBloqueado(nombre2, nombre1);

        if (bloqueadoPorDestino || bloqueadoPorRemitente) {
            String razon = bloqueadoPorDestino ? "El usuario te tiene bloqueado." : "Tienes bloqueado al usuario.";
            notificador.enviarMensaje("Sistema: Error al enviar mensaje privado a '" + nombre1 + "'. " + razon);
            return true;
        }
        return false;
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
        String mensajeCompleto = remitente.getNombreCliente() + ": " + mensaje;
        remitente.enviarMensaje("(Mensaje público enviado)");

        for (UnCliente cliente : servidor.getTodosLosClientes()) {
            enviarMensajeSiNoBloqueadoPublico(cliente, remitente, mensajeCompleto);
        }
    }

    private static void enviarMensajeSiNoBloqueadoPublico(UnCliente receptor, UnCliente remitente, String mensaje) throws IOException {
        if (receptor == remitente) return;

        String receptorNombre = receptor.getNombreCliente();
        String remitenteNombre = remitente.getNombreCliente();

        if (BDusuarios.estaBloqueado(receptorNombre, remitenteNombre) || BDusuarios.estaBloqueado(remitenteNombre, receptorNombre)) {
            return;
        }

        receptor.enviarMensaje(mensaje);
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