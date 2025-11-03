package servidormulti;

import bd.RUsuarios;
import java.io.IOException;

public class ControladorBloqueo {

    private final UnCliente cliente;

    public ControladorBloqueo(UnCliente cliente) {
        this.cliente = cliente;
    }

    public void manejarComando(String comandoCompleto) throws IOException {
        String[] partes = comandoCompleto.split(" ", 2);
        if (partes.length != 2) {
            cliente.enviarMensaje("Sistema: Uso incorrecto. Use /block <usuario> o /unblock <usuario>");
            return;
        }

        String comando = partes[0];
        String usuarioObjetivo = partes[1];
        String usuarioActual = cliente.getNombreCliente();

        if (usuarioObjetivo.equalsIgnoreCase(usuarioActual)) {
            cliente.enviarMensaje("Sistema: No puedes bloquearte a ti mismo.");
            return;
        }

        if (comando.equals("/block")) {
            bloquear(usuarioActual, usuarioObjetivo);
        } else if (comando.equals("/unblock")) {
            desbloquear(usuarioActual, usuarioObjetivo);
        }
    }

    private void bloquear(String actual, String objetivo) throws IOException {
        if (!RUsuarios.UsuarioExistente(objetivo)) {
            cliente.enviarMensaje("Sistema: El usuario '" + objetivo + "' no existe.");
            return;
        }

        if (RUsuarios.bloquearUsuario(actual, objetivo)) {
            cliente.enviarMensaje("Sistema: Has bloqueado a '" + objetivo + "'.");
        } else {
            cliente.enviarMensaje("Sistema: Error al bloquear a '" + objetivo + "'.");
        }
    }

    private void desbloquear(String actual, String objetivo) throws IOException {
        if (RUsuarios.desbloquearUsuario(actual, objetivo)) {
            cliente.enviarMensaje("Sistema: Has desbloqueado a '" + objetivo + "'.");
        } else {
            cliente.enviarMensaje("Sistema: Error al desbloquear a '" + objetivo + "'.");
        }
    }
}