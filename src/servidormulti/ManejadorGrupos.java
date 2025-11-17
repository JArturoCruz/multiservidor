package servidormulti;

import bd.RGrupos;
import bd.RUsuarios; // Importado para la validación de bloqueo
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
        else if (comando.equals("/ginvite")) manejarInvitarAGrupo(mensaje); // NUEVO: manejarInvitarAGrupo
    }

    private void manejarCrearGrupo(String mensaje) throws IOException {
        String groupName = parsearArgumentoUnico(mensaje, "/gcreate");
        if (groupName == null) return;
        if (groupName.equalsIgnoreCase(RGrupos.NOMBRE_TODOS)) {
            cliente.enviarMensaje("Sistema: No puedes crear el grupo '" + RGrupos.NOMBRE_TODOS + "'.");
            return;
        }
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

        if (!RGrupos.esAdministradorGrupo(groupName, cliente.getNombreCliente())) {
            String admin = RGrupos.obtenerAdminGrupo(groupName);
            cliente.enviarMensaje("Sistema: Solo el administrador (" + admin + ") puede eliminar el grupo '" + groupName + "'.");
            return;
        }

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

    // -----------------------------------------------------------------------------------------------------------------------------------
    // NUEVO MÉTODO: manejarInvitarAGrupo
    // -----------------------------------------------------------------------------------------------------------------------------------
    private void manejarInvitarAGrupo(String mensaje) throws IOException {
        String invitadoNombre = parsearArgumentoUnico(mensaje, "/ginvite");
        if (invitadoNombre == null) return;

        String clienteNombre = cliente.getNombreCliente();
        String currentGroupName = cliente.getCurrentGroupName();
        int currentGroupId = cliente.getCurrentGroupId();

        // 1. No se permite invitar en el grupo "Todos"
        if (currentGroupId == RGrupos.ID_TODOS) {
            cliente.enviarMensaje("Sistema: Solo puedes invitar a otros grupos. El grupo 'Todos' es público.");
            return;
        }

        // 2. Validación de auto-invitación
        if (clienteNombre.equalsIgnoreCase(invitadoNombre)) {
            cliente.enviarMensaje("Sistema: No puedes invitarte a ti mismo.");
            return;
        }

        // 3. Validación de conexión y existencia (el invitado debe estar conectado)
        UnCliente invitadoCliente = servidor.getCliente(invitadoNombre);
        if (invitadoCliente == null || !servidor.clienteEstaConectado(invitadoNombre)) {
            cliente.enviarMensaje("Sistema: El usuario '" + invitadoNombre + "' no está conectado o no existe.");
            return;
        }

        // 4. Validación: El invitado debe estar autenticado (no puede ser un anónimo)
        if (invitadoCliente.getNombreCliente().toLowerCase().startsWith("anonimo")) {
            cliente.enviarMensaje("Sistema: No puedes invitar a un usuario anónimo. Debe registrarse o iniciar sesión.");
            return;
        }

        // 5. Validación de bloqueo (bidireccional)
        if (RUsuarios.estaBloqueado(clienteNombre, invitadoNombre)) {
            cliente.enviarMensaje("Sistema: No puedes invitar a '" + invitadoNombre + "': lo tienes bloqueado.");
            return;
        }
        if (RUsuarios.estaBloqueado(invitadoNombre, clienteNombre)) {
            cliente.enviarMensaje("Sistema: No puedes invitar a '" + invitadoNombre + "': te tiene bloqueado.");
            return;
        }

        // 6. Validación de membresía (ya está en el grupo)
        List<String> miembros = RGrupos.obtenerMiembrosGrupo(currentGroupId);
        if (miembros.contains(invitadoNombre)) {
            cliente.enviarMensaje("Sistema: El usuario '" + invitadoNombre + "' ya es miembro del grupo '" + currentGroupName + "'.");
            return;
        }

        // 7. (Restricción por Admin) Solo el administrador puede invitar
        if (!RGrupos.esAdministradorGrupo(currentGroupName, clienteNombre)) {
            String admin = RGrupos.obtenerAdminGrupo(currentGroupName);
            cliente.enviarMensaje("Sistema: Solo el administrador (" + admin + ") puede invitar a nuevos miembros a '" + currentGroupName + "'.");
            return;
        }

        // --- Éxito: Invitar al usuario (Uniéndolo al grupo) ---
        if (RGrupos.unirUsuarioAGrupo(invitadoNombre, currentGroupId)) {
            // Notificación al inviter
            cliente.enviarMensaje("Sistema: Has invitado a '" + invitadoNombre + "' al grupo '" + currentGroupName + "'.");

            // Notificación al invitado
            invitadoCliente.enviarMensaje("Sistema: Has sido invitado al grupo '" + currentGroupName + "' por " + clienteNombre + ".");
            invitadoCliente.enviarMensaje("Sistema: El administrador te ha unido al grupo. Usa /join " + currentGroupName + " para cambiarte inmediatamente.");
        } else {
            cliente.enviarMensaje("Sistema: Error desconocido al intentar invitar a '" + invitadoNombre + "'.");
        }
    }

    private String parsearArgumentoUnico(String mensaje, String comando) throws IOException {
        String[] partes = mensaje.split(" ", 2);
        if (partes.length != 2 || partes[1].trim().isEmpty()) {
            cliente.enviarMensaje("Sistema: Uso incorrecto. " + comando + " <nombre_usuario>");
            return null;
        }
        return partes[1].trim();
    }
}