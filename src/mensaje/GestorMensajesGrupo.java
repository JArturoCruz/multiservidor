package mensaje;

import bd.RGrupos;
import servidormulti.ServidorMulti;
import servidormulti.UnCliente;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class GestorMensajesGrupo {

    public boolean difundirMensajeGrupo(String msg, UnCliente r, ServidorMulti s) throws IOException {
        long newMsgId = RGrupos.guardarMensajeGrupo(r.getCurrentGroupId(), r.getNombreCliente(), msg);
        if (newMsgId == -1) {
            r.enviarMensaje("Sistema: Error interno al guardar el mensaje.");
            return false;
        }

        RGrupos.actualizarUltimoMensajeVisto(r.getNombreCliente(), r.getCurrentGroupId(), newMsgId);
        r.enviarMensaje("(Mensaje enviado a " + r.getCurrentGroupName() + ")");

        String msgFmt = "[" + r.getCurrentGroupName() + "] " + r.getNombreCliente() + ": " + msg;
        Collection<UnCliente> destinatarios = obtenerDestinatariosOnline(r.getCurrentGroupId(), s);

        enviarADestinatariosDeGrupo(destinatarios, r, msgFmt, newMsgId);
        return true;
    }

    private Collection<UnCliente> obtenerDestinatariosOnline(int gId, ServidorMulti s) {
        if (gId == RGrupos.ID_TODOS) {
            return s.getTodosLosClientes();
        } else {
            List<String> miembros = RGrupos.obtenerMiembrosGrupo(gId);
            return miembros.stream()
                    .map(s::getCliente)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    private void enviarADestinatariosDeGrupo(Collection<UnCliente> dest, UnCliente r, String msgFmt, long newMsgId) {
        for (UnCliente d : dest) {
            if (d == r || d.getCurrentGroupId() != r.getCurrentGroupId()) continue;

            boolean bloqueado = bd.RUsuarios.estaBloqueado(d.getNombreCliente(), r.getNombreCliente()) ||
                    bd.RUsuarios.estaBloqueado(r.getNombreCliente(), d.getNombreCliente());

            if (!bloqueado) {
                try {
                    d.enviarMensaje(msgFmt);
                    RGrupos.actualizarUltimoMensajeVisto(d.getNombreCliente(), d.getCurrentGroupId(), newMsgId);
                } catch (IOException e) {
                }
            }
        }
    }
}