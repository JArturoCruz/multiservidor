package servidormulti.estado;

import mensaje.Mensaje;
import servidormulti.UnCliente;
import java.io.IOException;

public class EstadoInvitado implements EstadoCliente {

    private final UnCliente cliente;

    public EstadoInvitado(UnCliente cliente) {
        this.cliente = cliente;
    }

    @Override
    public void procesarMensaje(String mensaje) throws IOException {
        if (cliente.getMensajesGratisEnviados() < UnCliente.LIMITE_MENSAJES_GRATIS) {
            if (mensaje.startsWith("/") || mensaje.startsWith("@")) {
                cliente.enviarMensaje("Sistema: Los invitados no pueden usar comandos ni mensajes privados. Registre una cuenta.");
            } else {
                Mensaje.procesar(mensaje, cliente, cliente.getServidor());
                cliente.incrementarMensajesGratisEnviados();
            }
        } else {
            cliente.enviarMensaje("Sistema: Límite de mensajes gratis alcanzado. Por favor, regístrate o inicia sesión.");
        }
    }
}