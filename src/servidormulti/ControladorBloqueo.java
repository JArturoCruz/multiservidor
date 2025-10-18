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
        String miNombre = cliente.getNombreCliente();
        String objetivoNombre;

        if (partes.length < 2) {
            cliente.enviarMensaje("Sistema: Uso incorrecto. Usa: " + comando + " <nombre_usuario>");
            return;
        }

        objetivoNombre = partes[1];

        if (miNombre.equalsIgnoreCase(objetivoNombre)) {
            cliente.enviarMensaje("Sistema: No puedes bloquearte o desbloquearte a ti mismo.");
            return;
        }

        if (!BDusuarios.UsuarioExistente(objetivoNombre)) {
            cliente.enviarMensaje("Sistema: El usuario '" + objetivoNombre + "' no está registrado y no puede ser bloqueado/desbloqueado.");
            return;
        }
        boolean bloqueadoPorMi = BDusuarios.estaBloqueado(miNombre, objetivoNombre);

        if (comando.equalsIgnoreCase("/block")) {
            if (bloqueadoPorMi) {
                cliente.enviarMensaje("Sistema: Ya tienes bloqueado a '" + objetivoNombre + "'.");
            } else {
                boolean exito = BDusuarios.bloquearUsuario(miNombre, objetivoNombre);
                if (exito) {
                    cliente.enviarMensaje("Sistema: Has bloqueado a '" + objetivoNombre + "'. La comunicación se ha detenido.");
                    System.out.println(miNombre + " ha bloqueado a " + objetivoNombre);
                } else {
                    cliente.enviarMensaje("Sistema: Error al bloquear a '" + objetivoNombre + "'.");
                }
            }

        } else if (comando.equalsIgnoreCase("/unblock")) {
            if (!bloqueadoPorMi) {
                cliente.enviarMensaje("Sistema: El usuario '" + objetivoNombre + "' no está bloqueado por ti.");
            } else {
                boolean exito = BDusuarios.desbloquearUsuario(miNombre, objetivoNombre);

                if (exito) {
                    cliente.enviarMensaje("Sistema: Has desbloqueado a '" + objetivoNombre + "'. La comunicación se ha reanudado.");
                    System.out.println(miNombre + " ha desbloqueado a " + objetivoNombre);
                } else {
                    cliente.enviarMensaje("Sistema: Error al desbloquear a '" + objetivoNombre + "'.");
                }
            }
        }
    }
}