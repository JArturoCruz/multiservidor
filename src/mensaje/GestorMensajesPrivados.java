package mensaje;

import bd.RUsuarios;
import servidormulti.ServidorMulti;
import servidormulti.UnCliente;
import java.io.IOException;

public class GestorMensajesPrivados {

    public boolean enviarMensajePrivado(String msgCompleto, UnCliente r, ServidorMulti s) throws IOException {
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

    private void enviarAClientesPrivados(UnCliente r, String destStr, String msgFmt, ServidorMulti s, String contenido) throws IOException {
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

    private boolean procesarDestinatarioPrivado(UnCliente r, String dNombre, String msgFmt, ServidorMulti s) throws IOException {
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

    private boolean esBloqueoBidireccional(String dNombre, String rNombre, UnCliente notificador) throws IOException {
        if (dNombre.startsWith("anonimo") || rNombre.startsWith("anonimo")) return false;

        boolean bloqueadoPorDestino = RUsuarios.estaBloqueado(dNombre, rNombre);
        boolean bloqueadoPorRemitente = RUsuarios.estaBloqueado(rNombre, dNombre);

        if (bloqueadoPorDestino || bloqueadoPorRemitente) {
            String razon = bloqueadoPorDestino ? "El usuario te tiene bloqueado." : "Tienes bloqueado al usuario.";
            notificador.enviarMensaje("Sistema: Error al enviar mensaje privado a '" + dNombre + "'. " + razon);
            return true;
        }
        return false;
    }

    public void enviarMensajePrivadoJuego(String msg, UnCliente r, String dNombre, ServidorMulti s) throws IOException {
        UnCliente d = s.getCliente(dNombre);
        if (d != null) {
            d.enviarMensaje(formatearMensajePrivado(r.getNombreCliente(), msg));
            r.enviarMensaje(formatearConfirmacionPrivada(dNombre, msg));
        } else {
            r.enviarMensaje("Sistema: Error interno. El oponente (" + dNombre + ") no está conectado.");
        }
    }

    private String formatearMensajePrivado(String r, String c) { return "(Privado de " + r + "): " + c; }
    private String formatearConfirmacionPrivada(String d, String c) { return "(Mensaje privado para " + d + "): " + c; }
}