package mensaje;
import servidormulti.ServidorMulti;
import servidormulti.UnCliente;
import bd.BDusuarios;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Mensaje {

    public static boolean procesar(String mensaje, UnCliente remitente, ServidorMulti servidor) throws IOException {
        if (mensaje.trim().isEmpty()) {
            remitente.enviarMensaje("Sistema: No puedes enviar un mensaje vacío.");
            return false;
        }
        if (mensaje.startsWith("@")) {
            return enviarMensajePrivado(mensaje, remitente, servidor);
        } else {
            return difundirMensajeGrupo(mensaje, remitente, servidor);
        }
    }

    private static boolean difundirMensajeGrupo(String msg, UnCliente r, ServidorMulti s) throws IOException {
        long newMsgId = BDusuarios.guardarMensajeGrupo(r.getCurrentGroupId(), r.getNombreCliente(), msg);
        if (newMsgId == -1) {
            r.enviarMensaje("Sistema: Error interno al guardar el mensaje.");
            return false;
        }

        BDusuarios.actualizarUltimoMensajeVisto(r.getNombreCliente(), r.getCurrentGroupId(), newMsgId);
        r.enviarMensaje("(Mensaje enviado a " + r.getCurrentGroupName() + ")");

        String msgFmt = "[" + r.getCurrentGroupName() + "] " + r.getNombreCliente() + ": " + msg;
        Collection<UnCliente> destinatarios = obtenerDestinatariosOnline(r.getCurrentGroupId(), s);

        enviarADestinatariosDeGrupo(destinatarios, r, msgFmt, newMsgId);
        return true;
    }

    private static Collection<UnCliente> obtenerDestinatariosOnline(int gId, ServidorMulti s) {
        if (gId == BDusuarios.ID_TODOS) {
            return s.getTodosLosClientes();
        } else {
            List<String> miembros = BDusuarios.obtenerMiembrosGrupo(gId);
            return miembros.stream()
                    .map(s::getCliente)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    private static void enviarADestinatariosDeGrupo(Collection<UnCliente> destinatarios, UnCliente r, String msgFmt, long newMsgId) {
        for (UnCliente d : destinatarios) {
            if (d == r || d.getCurrentGroupId() != r.getCurrentGroupId()) continue;

            boolean bloqueado = BDusuarios.estaBloqueado(d.getNombreCliente(), r.getNombreCliente()) ||
                    BDusuarios.estaBloqueado(r.getNombreCliente(), d.getNombreCliente());

            if (!bloqueado) {
                try {
                    d.enviarMensaje(msgFmt);
                    BDusuarios.actualizarUltimoMensajeVisto(d.getNombreCliente(), d.getCurrentGroupId(), newMsgId);
                } catch (IOException e) {
                }
            }
        }
    }

    private static boolean enviarMensajePrivado(String msgCompleto, UnCliente r, ServidorMulti s) throws IOException {
        if (!r.isAutenticado()) {
            r.enviarMensaje("Sistema: Los invitados no pueden enviar mensajes privados. Regístrate.");
            return false;
        }

        String[] partes = msgCompleto.split(" ", 2);
        String destinatariosStr = partes[0].substring(1);
        String msgPrivado = (partes.length > 1) ? partes[1] : "";

        if (msgPrivado.isEmpty()) {
            r.enviarMensaje("Sistema: No puedes enviar un mensaje privado vacío.");
            return false;
        }

        String msgFmt = formatearMensajePrivado(r.getNombreCliente(), msgPrivado);
        enviarAClientesPrivados(r, destinatariosStr, msgFmt, s, msgPrivado);
        return true;
    }

    private static void enviarAClientesPrivados(UnCliente r, String destStr, String msgFmt, ServidorMulti s, String contenido) throws IOException {
        String[] destinatarios = destStr.split(",");
        StringBuilder enviadosConExito = new StringBuilder();

        for (String destNombre : destinatarios) {
            if (procesarDestinatarioPrivado(r, destNombre.trim(), msgFmt, s)) {
                if (enviadosConExito.length() > 0) enviadosConExito.append(", ");
                enviadosConExito.append(destNombre.trim());
            }
        }

        if (enviadosConExito.length() > 0) {
            r.enviarMensaje(formatearConfirmacionPrivada(enviadosConExito.toString(), contenido));
        }
    }

    private static boolean procesarDestinatarioPrivado(UnCliente r, String dNombre, String msgFmt, ServidorMulti s) throws IOException {
        UnCliente d = s.getCliente(dNombre);
        if (d == null) {
            r.enviarMensaje("Sistema: El usuario '" + dNombre + "' no está conectado o no existe.");
            return false;
        }
        if (!d.isAutenticado()) {
            r.enviarMensaje("Sistema: Los mensajes privados solo son para usuarios autenticados.");
            return false;
        }
        if (esBloqueoBidireccional(dNombre, r.getNombreCliente(), r)) {
            return false;
        }

        d.enviarMensaje(msgFmt);
        return true;
    }

    private static boolean esBloqueoBidireccional(String dNombre, String rNombre, UnCliente notificador) throws IOException {
        if (dNombre.startsWith("anonimo") || rNombre.startsWith("anonimo")) return false;

        boolean bloqueadoPorDestino = BDusuarios.estaBloqueado(dNombre, rNombre);
        boolean bloqueadoPorRemitente = BDusuarios.estaBloqueado(rNombre, dNombre);

        if (bloqueadoPorDestino || bloqueadoPorRemitente) {
            String razon = bloqueadoPorDestino ? "El usuario te tiene bloqueado." : "Tienes bloqueado al usuario.";
            notificador.enviarMensaje("Sistema: Error al enviar mensaje privado a '" + dNombre + "'. " + razon);
            return true;
        }
        return false;
    }

    public static void enviarMensajePrivadoEntreJugadores(String msg, UnCliente r, String dNombre, ServidorMulti s) throws IOException {
        UnCliente d = s.getCliente(dNombre);
        if (d != null) {
            d.enviarMensaje(formatearMensajePrivado(r.getNombreCliente(), msg));
            r.enviarMensaje(formatearConfirmacionPrivada(dNombre, msg));
        } else {
            r.enviarMensaje("Sistema: Error interno. El oponente (" + dNombre + ") no está conectado.");
        }
    }


    public static void notificarATodos(String notificacion, UnCliente clienteExcluido, ServidorMulti servidor) {
        System.out.println(notificacion);
        for (UnCliente cliente : servidor.getTodosLosClientes()) {
            if (cliente != clienteExcluido && cliente.getCurrentGroupId() == BDusuarios.ID_TODOS) {
                intentarEnviarNotificacion(cliente, notificacion);
            }
        }
    }

    private static void intentarEnviarNotificacion(UnCliente cliente, String notificacion) {
        try {
            cliente.enviarMensaje("Sistema: " + notificacion);
        } catch (IOException e) {
        }
    }

    private static String formatearMensajePrivado(String r, String c) { return "(Privado de " + r + "): " + c; }
    private static String formatearConfirmacionPrivada(String d, String c) { return "(Mensaje privado para " + d + "): " + c; }
}