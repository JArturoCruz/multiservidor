package mensaje;

import servidormulti.ServidorMulti;
import servidormulti.UnCliente;
import java.io.IOException;

public class Mensaje {

    private static final GestorMensajesGrupo gestorGrupo = new GestorMensajesGrupo();
    private static final GestorMensajesPrivados gestorPrivado = new GestorMensajesPrivados();

    public static boolean procesar(String mensaje, UnCliente remitente, ServidorMulti servidor) throws IOException {
        if (mensaje.trim().isEmpty()) {
            remitente.enviarMensaje("Sistema: No puedes enviar un mensaje vacío.");
            return false;
        }

        if (mensaje.startsWith("@")) {
            return gestorPrivado.enviarMensajePrivado(mensaje, remitente, servidor);
        } else {
            return gestorGrupo.difundirMensajeGrupo(mensaje, remitente, servidor);
        }
    }

    public static void enviarMensajePrivadoEntreJugadores(String msg, UnCliente r, String dNombre, ServidorMulti s) throws IOException {
        gestorPrivado.enviarMensajePrivadoJuego(msg, r, dNombre, s);
    }

    public static void notificarATodos(String notificacion, UnCliente clienteExcluido, ServidorMulti servidor) {
        System.out.println(notificacion);
        for (UnCliente cliente : servidor.getTodosLosClientes()) {
            if (cliente != clienteExcluido && cliente.getCurrentGroupId() == bd.RGrupos.ID_TODOS) {
                intentarEnviarNotificacion(cliente, notificacion);
            }
        }
    }

    public static void notificarAlGrupo(String notificacion, UnCliente clienteExcluido, ServidorMulti servidor) {
        if (clienteExcluido == null) return;
        int grupoId = clienteExcluido.getCurrentGroupId();
        if (grupoId == bd.RGrupos.ID_TODOS) return;

        System.out.println(notificacion + " (Grupo: " + clienteExcluido.getCurrentGroupName() + ")");

        for (UnCliente cliente : servidor.getTodosLosClientes()) {
            if (cliente != clienteExcluido && cliente.getCurrentGroupId() == grupoId) {
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
}