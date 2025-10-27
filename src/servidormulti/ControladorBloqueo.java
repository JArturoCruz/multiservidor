package servidormulti;

import bd.BDusuarios;
import java.io.IOException;

public class ControladorBloqueo {

    private final UnCliente cliente;

    public ControladorBloqueo(UnCliente cliente) {
        this.cliente = cliente;
    }

    public void manejarComando(String mensaje) throws IOException {
        if (!cliente.isAutenticado()) {
            cliente.enviarMensaje("Sistema: Debes estar autenticado para usar los comandos de bloqueo.");
            return;
        }

        String[] partes = mensaje.trim().split(" ");
        String comando = partes[0];

        if (!validarArgumentos(partes)) return;

        String objetivo = partes[1];
        if (!validarObjetivo(comando, objetivo)) return;

        if (comando.equalsIgnoreCase("/block")) {
            procesarBloqueo(objetivo);
        } else if (comando.equalsIgnoreCase("/unblock")) {
            procesarDesbloqueo(objetivo);
        }
    }

    private boolean validarArgumentos(String[] partes) throws IOException {
        if (partes.length < 2) {
            cliente.enviarMensaje("Sistema: Uso incorrecto. Usa: " + partes[0] + " <nombre_usuario>");
            return false;
        }
        return true;
    }

    private boolean validarObjetivo(String comando, String objetivo) throws IOException {
        String miNombre = cliente.getNombreCliente();

        if (miNombre.equalsIgnoreCase(objetivo)) {
            cliente.enviarMensaje("Sistema: No puedes bloquearte o desbloquearte a ti mismo.");
            return false;
        }

        if (!BDusuarios.UsuarioExistente(objetivo)) {
            cliente.enviarMensaje("Sistema: El usuario '" + objetivo + "' no está registrado y no puede ser bloqueado/desbloqueado.");
            return false;
        }
        return true;
    }

    private void procesarBloqueo(String objetivo) throws IOException {
        String miNombre = cliente.getNombreCliente();

        if (BDusuarios.estaBloqueado(miNombre, objetivo)) {
            cliente.enviarMensaje("Sistema: Ya tienes bloqueado a '" + objetivo + "'.");
            return;
        }

        boolean exito = BDusuarios.bloquearUsuario(miNombre, objetivo);

        if (exito) {
            notificarBloqueoExitoso(miNombre, objetivo);
        } else {
            cliente.enviarMensaje("Sistema: Error al bloquear a '" + objetivo + "'.");
        }
    }

    private void notificarBloqueoExitoso(String miNombre, String objetivo) throws IOException {
        cliente.enviarMensaje("Sistema: Has bloqueado a '" + objetivo + "'. La comunicación se ha detenido.");
        System.out.println(miNombre + " ha bloqueado a " + objetivo);
    }

    private void procesarDesbloqueo(String objetivo) throws IOException {
        String miNombre = cliente.getNombreCliente();

        if (!BDusuarios.estaBloqueado(miNombre, objetivo)) {
            cliente.enviarMensaje("Sistema: El usuario '" + objetivo + "' no está bloqueado por ti.");
            return;
        }

        boolean exito = BDusuarios.desbloquearUsuario(miNombre, objetivo);

        if (exito) {
            notificarDesbloqueoExitoso(miNombre, objetivo);
        } else {
            cliente.enviarMensaje("Sistema: Error al desbloquear a '" + objetivo + "'.");
        }
    }

    private void notificarDesbloqueoExitoso(String miNombre, String objetivo) throws IOException {
        cliente.enviarMensaje("Sistema: Has desbloqueado a '" + objetivo + "'. La comunicación se ha reanudado.");
        System.out.println(miNombre + " ha desbloqueado a " + objetivo);
    }
}