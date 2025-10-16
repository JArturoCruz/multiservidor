package servidormulti;

import java.io.IOException;
import mensaje.Mensaje;

public class ControladorMensajesInvitado {

    private final UnCliente cliente;

    public ControladorMensajesInvitado(UnCliente cliente) {
        this.cliente = cliente;
    }

    private void notificarLimiteAgotado() throws IOException {
        cliente.enviarMensaje("Sistema: No puedes enviar más mensajes. Debes usar '/login <nombre_usuario> <PIN>' o '/register <nombre_usuario> <PIN>' para continuar enviando.");
    }

    private boolean validarMensajeVacio(String mensaje) throws IOException {
        if (!mensaje.startsWith("@") && mensaje.trim().isEmpty()) {
            cliente.enviarMensaje("Sistema: No puedes enviar un mensaje público vacío.");
            return false;
        }
        return true;
    }

    private void manejarContadorMensajesGratis() throws IOException {
        cliente.incrementarMensajesGratisEnviados();
        int restantes = cliente.getLimiteMensajesGratis() - cliente.getMensajesGratisEnviados();

        if (restantes > 0) {
            cliente.enviarMensaje("Sistema: Mensaje enviado. Te quedan " + restantes + " mensajes gratis.");
        } else {
            cliente.enviarMensaje("Sistema:Has agotado tus mensajes gratis (" + cliente.getLimiteMensajesGratis() + "). Por favor, usa '/login <nombre_usuario> <PIN>' o '/register <nombre_usuario> <PIN>' para continuar enviando.");
        }
    }

    public void manejarMensaje(String mensaje, ServidorMulti servidor) throws IOException {
        if (cliente.getMensajesGratisEnviados() >= cliente.getLimiteMensajesGratis()) {
            notificarLimiteAgotado();
            return;
        }

        if (!validarMensajeVacio(mensaje)) return;

        boolean mensajeValido = Mensaje.procesar(mensaje, cliente, servidor);

        if (mensajeValido) {
            manejarContadorMensajesGratis();
        }
    }
}