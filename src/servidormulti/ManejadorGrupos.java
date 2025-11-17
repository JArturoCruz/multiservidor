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
    }

    private void manejarCrearGrupo(String mensaje) throws IOException {
        String groupName = parsearArgumentoUnico(mensaje, "/gcreate");
        if (groupName == null) return;
        if (groupName.equalsIgnoreCase(RGrupos.NOMBRE_TODOS)) {
            cliente.enviarMensaje("Sistema: No puedes crear el grupo '" + RGrupos.NOMBRE_TODOS + "'.");
            return;
        }
        // Se pasa el nombre del cliente como administrador
        if (RGrupos.crearGrupo(groupName, cliente.getNombreCliente())) {
            cliente.enviarMensaje("Sistema: Grupo '" + groupName + "' creado con éxito. Eres el administrador.");
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

        // --- VALIDACIÓN DE ADMINISTRADOR ---
        if (!RGrupos.esAdministradorGrupo(groupName, cliente.getNombreCliente())) {
            String admin = RGrupos.obtenerAdminGrupo(groupName);
            cliente.enviarMensaje("Sistema: Solo el administrador (" + admin + ") puede eliminar el grupo '" + groupName + "'.");
            return;
        }
        // -----------------------------------

        if (RGrupos.eliminarGrupo(groupName)) {
            cliente.enviarMensaje("Sistema: Grupo '" + groupName + "' eliminado con éxito.");
            notificarMiembrosGrupoEliminado(groupName);
        } else {
            cliente.enviarMensaje("Sistema: Error al eliminar grupo '" + groupName + "'. El grupo podría no existir.");
        }
    }

    private void notificarMiembrosGrupoEliminado(String groupName) throws IOException {
        for (UnCliente c : servidor.getTodosLosClientes()) {
            if (c.getCurrentGroupName().equalsIgnoreCase(groupName)) {
                c.setCurrentGroup(RGrupos.ID_TODOS, RGrupos.NOMBRE_TODOS);
                c.enviarMensaje("Sistema: El grupo '" + groupName + "' fue eliminado por el administrador.");
                c.enviarMensaje("Sistema: Has sido movido automáticamente al grupo '" + RGrupos.NOMBRE_TODOS + "'.");
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
        cliente.enviarMensaje("Sistema: Te has unido y cambiado al grupo '" + groupName + "'.");
        cliente.enviarMensajesPendientes();
    }

    private void manejarListarGrupos() throws IOException {
        List<String> grupos = RGrupos.obtenerTodosLosGrupos();
        cliente.enviarMensaje("Sistema: --- Grupos Disponibles ---");
        for (String g : grupos) {
            String admin = RGrupos.obtenerAdminGrupo(g);
            String infoAdmin = (admin != null && !admin.equals("SYSTEM")) ? " (Admin: " + admin + ")" : "";
            cliente.enviarMensaje(" - " + g + infoAdmin);
        }
        cliente.enviarMensaje("Sistema: ---------------------------");
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