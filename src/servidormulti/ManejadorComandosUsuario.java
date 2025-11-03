package servidormulti;

import bd.BDusuarios;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ManejadorComandosUsuario {

    private final UnCliente cliente;
    private final ServidorMulti servidor;
    private final FormateadorMensajes formateador;

    public ManejadorComandosUsuario(UnCliente cliente, ServidorMulti servidor) {
        this.cliente = cliente;
        this.servidor = servidor;
        this.formateador = new FormateadorMensajes();
    }

    public void manejar(String mensaje, String comando) throws IOException {
        if (comando.equals("/gcreate")) manejarCrearGrupo(mensaje);
        else if (comando.equals("/gdelete")) manejarEliminarGrupo(mensaje);
        else if (comando.equals("/join")) manejarUnirseGrupo(mensaje);
        else if (comando.equals("/glist")) manejarListarGrupos();
        else if (comando.equals("/ranking")) manejarRanking();
        else if (comando.equals("/vs")) manejarVs(mensaje);
    }

    private void manejarCrearGrupo(String mensaje) throws IOException {
        String groupName = parsearArgumentoUnico(mensaje, "/gcreate");
        if (groupName == null) return;

        if (groupName.equalsIgnoreCase(BDusuarios.NOMBRE_TODOS)) {
            cliente.enviarMensaje("Sistema: No puedes crear el grupo '" + BDusuarios.NOMBRE_TODOS + "'.");
            return;
        }

        if (BDusuarios.crearGrupo(groupName, cliente.getNombreCliente())) {
            cliente.enviarMensaje("Sistema: Grupo '" + groupName + "' creado.");
            manejarUnirseGrupo("/join " + groupName); // Unirse automáticamente
        } else {
            cliente.enviarMensaje("Sistema: Error al crear el grupo (quizás ya existe).");
        }
    }

    private void manejarEliminarGrupo(String mensaje) throws IOException {
        String groupName = parsearArgumentoUnico(mensaje, "/gdelete");
        if (groupName == null) return;

        if (groupName.equalsIgnoreCase(BDusuarios.NOMBRE_TODOS)) {
            cliente.enviarMensaje("Sistema: No puedes eliminar el grupo '" + BDusuarios.NOMBRE_TODOS + "'.");
            return;
        }

        if (BDusuarios.eliminarGrupo(groupName)) {
            cliente.enviarMensaje("Sistema: Grupo '" + groupName + "' eliminado.");
            notificarMiembrosGrupoEliminado(groupName);
        } else {
            cliente.enviarMensaje("Sistema: Error al eliminar el grupo (quizás no existe).");
        }
    }

    private void notificarMiembrosGrupoEliminado(String groupName) throws IOException {
        for (UnCliente c : servidor.getTodosLosClientes()) {
            if (c.getCurrentGroupName().equalsIgnoreCase(groupName)) {
                c.setCurrentGroup(BDusuarios.ID_TODOS, BDusuarios.NOMBRE_TODOS);
                c.enviarMensaje("Sistema: El grupo '" + groupName + "' fue eliminado. Has sido movido a '" + BDusuarios.NOMBRE_TODOS + "'.");
                c.enviarMensajesPendientes();
            }
        }
    }

    private void manejarUnirseGrupo(String mensaje) throws IOException {
        String groupName = parsearArgumentoUnico(mensaje, "/join");
        if (groupName == null) return;

        int groupId = BDusuarios.obtenerGrupoIdPorNombre(groupName);
        if (groupId == -1) {
            cliente.enviarMensaje("Sistema: El grupo '" + groupName + "' no existe.");
            return;
        }

        if (cliente.getCurrentGroupId() == groupId) {
            cliente.enviarMensaje("Sistema: Ya estás en el grupo '" + groupName + "'.");
            return;
        }

        if (groupId != BDusuarios.ID_TODOS) {
            BDusuarios.unirUsuarioAGrupo(cliente.getNombreCliente(), groupId);
        }

        cliente.setCurrentGroup(groupId, groupName);
        cliente.enviarMensaje("Sistema: Te has unido al grupo '" + groupName + "'.");
        cliente.enviarMensajesPendientes();
    }

    private void manejarListarGrupos() throws IOException {
        List<String> grupos = BDusuarios.obtenerTodosLosGrupos();
        String respuesta = formateador.formatearListaGrupos(grupos);
        cliente.enviarMensaje(respuesta);
    }

    private void manejarRanking() throws IOException {
        List<BDusuarios.RankingEntry> ranking = BDusuarios.obtenerRankingGeneral();
        String respuesta = formateador.formatearRanking(ranking);
        cliente.enviarMensaje(respuesta);
    }

    private void manejarVs(String mensaje) throws IOException {
        String[] partes = mensaje.split(" ", 3);
        if (partes.length != 3) {
            cliente.enviarMensaje("Sistema VS: Uso incorrecto. Usa: /vs <usuario1> <usuario2>");
            return;
        }

        String user1 = partes[1].trim();
        String user2 = partes[2].trim();
        if (validarUsuariosVs(user1, user2)) {
            Map<String, Integer> stats = BDusuarios.obtenerEstadisticasVs(user1, user2);
            String respuesta = formateador.formatearVs(stats, user1, user2);
            cliente.enviarMensaje(respuesta);
        }
    }

    private boolean validarUsuariosVs(String user1, String user2) throws IOException {
        if (user1.equalsIgnoreCase(user2)) {
            cliente.enviarMensaje("Sistema VS: Los usuarios deben ser diferentes.");
            return false;
        }
        if (!BDusuarios.UsuarioExistente(user1) || !BDusuarios.UsuarioExistente(user2)) {
            cliente.enviarMensaje("Sistema VS: Asegúrate de que ambos usuarios existan.");
            return false;
        }
        return true;
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