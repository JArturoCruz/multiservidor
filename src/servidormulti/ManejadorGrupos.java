package servidormulti;

import bd.RGrupos;
import java.io.IOException;
import java.util.List;

public class ManejadorGrupos {

    private final UnCliente cliente;
    private final ServidorMulti servidor;
    private final FormateadorMensajes formateador;

    public ManejadorGrupos(UnCliente cliente, ServidorMulti servidor) {
        this.cliente = cliente;
        this.servidor = servidor;
        this.formateador = new FormateadorMensajes();
    }

    public void manejar(String mensaje, String comando) throws IOException {
        if (comando.equals("/gcreate")) manejarCrearGrupo(mensaje);
        else if (comando.equals("/gdelete")) manejarEliminarGrupo(mensaje);
        else if (comando.equals("/join")) manejarUnirseGrupo(mensaje);
        else if (comando.equals("/glist")) manejarListarGrupos();
        else if (comando.equals("/ginvite")) manejarInvitarAGrupo(mensaje);
        else if (comando.equals("/gleave")) manejarSalirGrupo(mensaje); // NUEVO
    }

    private void manejarCrearGrupo(String mensaje) throws IOException {
        String groupName = parsearArgumentoUnico(mensaje, "/gcreate");
        if (groupName == null) return;
        if (groupName.equalsIgnoreCase(RGrupos.NOMBRE_TODOS)) {
            cliente.enviarMensaje("Sistema: No puedes crear el grupo '" + RGrupos.NOMBRE_TODOS + "'.");
            return;
        }
        if (RGrupos.crearGrupo(groupName, cliente.getNombreCliente())) {
            cliente.enviarMensaje("Sistema: Grupo '" + groupName + "' creado.");
            manejarUnirseGrupo("/join " + groupName); // Unirse automáticamente
        } else {
            cliente.enviarMensaje("Sistema: Error al crear el grupo (quizás ya existe).");
        }
    }

    private void manejarEliminarGrupo(String mensaje) throws IOException {
        String groupName = parsearArgumentoUnico(mensaje, "/gdelete");
        if (groupName == null) return;
        if (groupName.equalsIgnoreCase(RGrupos.NOMBRE_TODOS)) {
            cliente.enviarMensaje("Sistema: No puedes eliminar el grupo '" + RGrupos.NOMBRE_TODOS + "'.");
            return;
        }

        if (!RGrupos.esAdministradorGrupo(groupName, cliente.getNombreCliente())) {
            cliente.enviarMensaje("Sistema: Solo el administrador (" + RGrupos.obtenerAdminGrupo(groupName) + ") puede eliminar el grupo '" + groupName + "'.");
            return;
        }
        if (RGrupos.eliminarGrupo(groupName)) {
            cliente.enviarMensaje("Sistema: Grupo '" + groupName + "' eliminado.");
            notificarMiembrosGrupoEliminado(groupName);
        } else {
            cliente.enviarMensaje("Sistema: Error al eliminar el grupo (quizás no existe).");
        }
    }

    private void notificarMiembrosGrupoEliminado(String groupName) throws IOException {
        for (UnCliente c : servidor.getTodosLosClientes()) {
            if (c.getCurrentGroupName().equalsIgnoreCase(groupName)) {
                c.setCurrentGroup(RGrupos.ID_TODOS, RGrupos.NOMBRE_TODOS);
                c.enviarMensaje("Sistema: El grupo '" + groupName + "' fue eliminado. Has sido movido a '" + RGrupos.NOMBRE_TODOS + "'.");
                c.enviarMensajesPendientes();
            }
        }
    }

    private void manejarUnirseGrupo(String mensaje) throws IOException {
        String groupName = parsearArgumentoUnico(mensaje, "/join");
        if (groupName == null) return;
        int groupId = RGrupos.obtenerGrupoIdPorNombre(groupName);
        if (groupId == -1) {
            cliente.enviarMensaje("Sistema: El grupo '" + groupName + "' no existe.");
            return;
        }
        if (cliente.getCurrentGroupId() == groupId) {
            cliente.enviarMensaje("Sistema: Ya estás en el grupo '" + groupName + "'.");
            return;
        }
        if (groupId != RGrupos.ID_TODOS) {
            RGrupos.unirUsuarioAGrupo(cliente.getNombreCliente(), groupId);
        }
        cliente.setCurrentGroup(groupId, groupName);
        cliente.enviarMensaje("Sistema: Te has unido al grupo '" + groupName + "'.");
        cliente.enviarMensajesPendientes();
    }

    private void manejarSalirGrupo(String mensaje) throws IOException {
        String groupName = parsearArgumentoUnico(mensaje, "/gleave");
        if (groupName == null) return;

        int groupId = RGrupos.obtenerGrupoIdPorNombre(groupName);
        String clienteNombre = cliente.getNombreCliente();

        if (groupId == -1) {
            cliente.enviarMensaje("Sistema: El grupo '" + groupName + "' no existe.");
            return;
        }

        if (groupId == RGrupos.ID_TODOS) {
            cliente.enviarMensaje("Sistema: No puedes salir del grupo principal 'Todos'.");
            return;
        }

        if (!RGrupos.esMiembroDeGrupo(clienteNombre, groupId)) {
            cliente.enviarMensaje("Sistema: No eres miembro del grupo '" + groupName + "'.");
            return;
        }

        if (RGrupos.esAdministradorGrupo(groupName, clienteNombre)) {
            cliente.enviarMensaje("Sistema: Eres el administrador de '" + groupName + "'. Debes eliminar el grupo con /gdelete o transferir la administración para salir.");
            return;
        }

        if (RGrupos.removerUsuarioDeGrupo(clienteNombre, groupId)) {

            // 1. Notificar a los que estaban viendo ese grupo
            String notificacion = clienteNombre + " ha abandonado el grupo '" + groupName + "'.";
            for (UnCliente c : servidor.getTodosLosClientes()) {
                if (c.getCurrentGroupId() == groupId) {
                    c.enviarMensaje("Sistema: " + notificacion);
                }
            }

            // 2. Si el usuario estaba en ese grupo, moverlo a "Todos"
            if (cliente.getCurrentGroupId() == groupId) {
                cliente.setCurrentGroup(RGrupos.ID_TODOS, RGrupos.NOMBRE_TODOS);
                cliente.enviarMensaje("Sistema: Has salido de '" + groupName + "'. Ahora estás en '" + RGrupos.NOMBRE_TODOS + "'.");
                cliente.enviarMensajesPendientes();
            } else {
                cliente.enviarMensaje("Sistema: Has salido de '" + groupName + "'.");
            }

        } else {
            cliente.enviarMensaje("Sistema: Error desconocido al intentar salir del grupo '" + groupName + "'.");
        }
    }

    private void manejarInvitarAGrupo(String mensaje) throws IOException {
        cliente.enviarMensaje("Sistema: Comando /ginvite en desarrollo.");
    }

    private void manejarListarGrupos() throws IOException {
        String respuesta = formateador.formatearListaGrupos(RGrupos.obtenerTodosLosGrupos());
        cliente.enviarMensaje(respuesta);
    }

    private String parsearArgumentoUnico(String mensaje, String comando) throws IOException {
        String[] partes = mensaje.split(" ", 2);
        if (partes.length != 2 || partes[1].trim().isEmpty()) {
            cliente.enviarMensaje("Sistema: Uso incorrecto. " + comando + " <nombre>");
            return null;
        }
        return partes[1].trim();
    }
}