package mensaje;

import bd.RGrupos;
import bd.RUsuarios;
import servidormulti.ServidorMulti;
import servidormulti.UnCliente;
import java.io.IOException;

public class GestorMensajesGrupo {

    public boolean difundirMensajeGrupo(String mensaje, UnCliente remitente, ServidorMulti servidor) throws IOException {
        String remitenteNombre = remitente.getNombreCliente();
        int groupId = remitente.getCurrentGroupId();
        String groupName = remitente.getCurrentGroupName();

        long messageId = RGrupos.guardarMensajeGrupo(groupId, remitenteNombre, mensaje);
        if (messageId == -1) {
            remitente.enviarMensaje("Sistema: Error al guardar mensaje en la BD.");
            return false;
        }

        String formattedMsg = "[" + groupName + "] " + remitenteNombre + ": " + mensaje;

        for (UnCliente cliente : servidor.getTodosLosClientes()) {
            String destinoNombre = cliente.getNombreCliente();

            if (!cliente.isAutenticado() && groupId != RGrupos.ID_TODOS) {
                continue;
            }

            if (RUsuarios.estaBloqueado(remitenteNombre, destinoNombre) || RUsuarios.estaBloqueado(destinoNombre, remitenteNombre)) {
                continue;
            }

            if (RGrupos.esMiembroDeGrupo(destinoNombre, groupId)) {
                try {
                    cliente.enviarMensaje(formattedMsg);
                } catch (IOException e) {
                }
            }
        }

        remitente.enviarMensaje("(Mensaje enviado a " + groupName + ")");
        RGrupos.actualizarUltimoMensajeVisto(remitenteNombre, groupId, messageId);

        return true;
    }
}