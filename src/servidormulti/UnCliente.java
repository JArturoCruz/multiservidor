package servidormulti;
import mensaje.Mensaje;
import bd.BDusuarios;
import bd.BDusuarios.RankingEntry;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UnCliente implements Runnable {

    final DataOutputStream salida;
    final DataInputStream entrada;
    private String nombreCliente;
    private int mensajesGratisEnviados = 0;
    private boolean autenticado = false;
    private static final int LIMITE_MENSAJES_GRATIS = 3;

    private int currentGroupId;
    private String currentGroupName;

    private final ServidorMulti servidor;

    private final AutenticadorCliente autenticador;
    private final ControladorBloqueo controladorBloqueo;
    private final ControladorJuego controladorJuego;
    private final ManejadorComandosJuego manejadorComandosJuego;

    UnCliente(Socket s, ServidorMulti servidor) throws IOException {
        salida = new DataOutputStream(s.getOutputStream());
        entrada = new DataInputStream(s.getInputStream());
        this.servidor = servidor;
        this.autenticador = new AutenticadorCliente(this, servidor);
        this.controladorBloqueo = new ControladorBloqueo(this);
        this.controladorJuego = servidor.getControladorJuego();
        this.manejadorComandosJuego = new ManejadorComandosJuego(this.controladorJuego, servidor.getGestorPropuestas());

        this.currentGroupId = BDusuarios.ID_TODOS;
        this.currentGroupName = BDusuarios.NOMBRE_TODOS;
    }

    public void enviarMensaje(String mensaje) throws IOException {
        this.salida.writeUTF(mensaje);
    }

    public String getNombreCliente() {
        return nombreCliente;
    }

    public void setNombreCliente(String nombreCliente) {
        this.nombreCliente = nombreCliente;
    }

    public void setAutenticado(boolean autenticado) {
        this.autenticado = autenticado;
    }

    public boolean isAutenticado() {
        return autenticado;
    }

    public int getMensajesGratisEnviados() {
        return mensajesGratisEnviados;
    }

    public void incrementarMensajesGratisEnviados() {
        this.mensajesGratisEnviados++;
    }

    public int getLimiteMensajesGratis() {
        return LIMITE_MENSAJES_GRATIS;
    }

    public void resetMensajesGratisEnviados() {
        this.mensajesGratisEnviados = 0;
    }

    public int getCurrentGroupId() {
        return currentGroupId;
    }

    public String getCurrentGroupName() {
        return currentGroupName;
    }

    public void setCurrentGroup(int groupId, String groupName) {
        this.currentGroupId = groupId;
        this.currentGroupName = groupName;
    }

    private void inicializarCliente() throws IOException {
        nombreCliente = servidor.generarNombreAnonimo();
        servidor.agregarCliente(nombreCliente, this);
        Mensaje.notificarATodos(nombreCliente + " se ha unido al chat (en " + this.currentGroupName + ").", this, servidor);
    }

    private void enviarMensajesDeBienvenida() throws IOException {
        enviarMensaje("Sistema: Bienvenido. Actualmente estás en el grupo: " + this.currentGroupName);
        enviarMensaje("Sistema: Como invitado (" + nombreCliente + "), solo puedes enviar " + LIMITE_MENSAJES_GRATIS + " mensajes en el grupo 'Todos'.");
        enviarMensaje("Sistema: Usa '/register <nombre_usuario> <PIN>' o '/login <nombre_usuario> <PIN>'.");
        enviarMensaje("Sistema: Los usuarios autenticados pueden usar:");
        enviarMensaje("Sistema: - '/glist' (Ver todos los grupos)");
        enviarMensaje("Sistema: - '/gcreate <nombre_grupo>' (Crear un grupo)");
        enviarMensaje("Sistema: - '/gdelete <nombre_grupo>' (Borrar un grupo si eres el creador - *funcionalidad simplificada: cualquiera puede borrar*)");
        enviarMensaje("Sistema: - '/join <nombre_grupo>' (Unirse y cambiar a un grupo)");
        enviarMensaje("Sistema: - '/block <usuario>' y '/unblock <usuario>'");
        enviarMensaje("Sistema: Juega al Gato (comandos /gato, /accept, /move, /ranking, /vs, etc.)");
    }

    private void enviarMensajesPendientes() throws IOException {
        if (!autenticado) return; // Los anónimos no tienen historial

        List<BDusuarios.MensajeGrupo> mensajes = BDusuarios.obtenerMensajesNoVistos(this.nombreCliente, this.currentGroupId);
        if (mensajes.isEmpty()) {
            enviarMensaje("Sistema: No hay mensajes nuevos en '" + this.currentGroupName + "'.");
            return;
        }

        enviarMensaje("Sistema: --- Mostrando mensajes no leídos para '" + this.currentGroupName + "' ---");
        long ultimoId = 0;
        for (BDusuarios.MensajeGrupo msg : mensajes) {
            enviarMensaje("[" + msg.timestamp + "] " + msg.sender + ": " + msg.content);
            ultimoId = msg.messageId;
        }

        if (ultimoId > 0) {
            BDusuarios.actualizarUltimoMensajeVisto(this.nombreCliente, this.currentGroupId, ultimoId);
        }
        enviarMensaje("Sistema: --- Fin de mensajes no leídos ---");
    }

    private void manejarDesconexion() {
        if (nombreCliente != null) {
            try {
                controladorJuego.finalizarPorDesconexion(this);
            } catch (IOException e) {
                System.out.println("Error al notificar desconexión de juego.");
            }
            servidor.removerCliente(nombreCliente);
            String msg = nombreCliente + " ha abandonado el chat.";
            System.out.println(msg);
            for (UnCliente cliente : servidor.getTodosLosClientes()) {
                if (cliente.getCurrentGroupId() == BDusuarios.ID_TODOS) {
                    try {
                        cliente.enviarMensaje("Sistema: " + msg);
                    } catch (IOException e) {}
                }
            }
        }
    }

    private void manejarErrorIO() {
        System.out.println("Error de comunicación con " + (nombreCliente != null ? nombreCliente : "un cliente"));
    }

    private void bucleDeLectura() throws IOException {
        while (true) {
            String mensaje = entrada.readUTF();
            procesarMensaje(mensaje);
        }
    }

    private void procesarMensaje(String mensaje) throws IOException {
        String comando = mensaje.split(" ", 2)[0].toLowerCase();

        if (comando.equals("/register") || comando.equals("/login")) {
            autenticador.manejarAutenticacion(mensaje);
        } else if (autenticado) {
            procesarMensajeAutenticado(mensaje, comando);
        } else {
            if (getMensajesGratisEnviados() < getLimiteMensajesGratis()) {
                if (mensaje.startsWith("/") || mensaje.startsWith("@")) {
                    enviarMensaje("Sistema: Los invitados no pueden usar comandos ni mensajes privados. Registre una cuenta.");
                } else if (mensaje.trim().isEmpty()) {
                    enviarMensaje("Sistema: No puedes enviar un mensaje vacío.");
                } else {
                    Mensaje.procesar(mensaje, this, servidor);
                    incrementarMensajesGratisEnviados();
                }
            } else {
                enviarMensaje("Sistema: Has alcanzado el límite de " + getLimiteMensajesGratis() + " mensajes para invitados. Por favor, regístrate o inicia sesión.");
            }
        }
    }

    private void procesarMensajeAutenticado(String mensaje, String comando) throws IOException {

        boolean estaEnInteraccionJuego = controladorJuego.estaJugando(nombreCliente) || controladorJuego.tieneRevanchaPendiente(nombreCliente);

        if (comando.equals("/gcreate")) {
            manejarCrearGrupo(mensaje);
        }
        else if (comando.equals("/gdelete")) {
            manejarEliminarGrupo(mensaje);
        }
        else if (comando.equals("/join")) {
            manejarUnirseGrupo(mensaje);
        }
        else if (comando.equals("/glist")) {
            manejarListarGrupos();
        }

        else if (comando.equals("/move") || comando.equals("/gato") || comando.equals("/accept") || comando.equals("/reject") || comando.equals("/si") || comando.equals("/no")) {
            manejadorComandosJuego.manejarComando(mensaje, this);
        }
        else if (comando.equals("/ranking")) {
            manejarRanking();
        }
        else if (comando.equals("/vs")) {
            String[] partes = mensaje.split(" ", 3);
            manejarVs(partes);
        }
        else if (estaEnInteraccionJuego) {
            if (mensaje.startsWith("@")) {
                String[] partes = mensaje.split(" ", 2);
                String nombreDestino = partes[0].substring(1);

                Set<String> oponentes = controladorJuego.getOponentesActivos(nombreCliente);

                if (oponentes.contains(nombreDestino)) {
                    Mensaje.enviarMensajePrivadoEntreJugadores(mensaje, this, nombreDestino, servidor);
                } else {
                    enviarMensaje("Sistema: Solo puedes enviar mensajes privados a tus oponentes activos mientras juegas.");
                }
            }
            else if (comando.startsWith("/")) {
                enviarMensaje("Sistema: Acción bloqueada. Solo se permiten comandos de juego, ranking (/ranking, /vs) y chat privado (@oponente) mientras estás en partida/revancha.");
            }
            else {
                enviarMensaje("Sistema: Chat público (grupo) bloqueado. Usa @<oponente> <mensaje> para hablar con tu oponente.");
            }
        }
        else if (comando.equals("/block") || comando.equals("/unblock")) {
            controladorBloqueo.manejarComando(mensaje);
        }
        else if (comando.startsWith("/")) {
            enviarMensaje("Sistema: Comando no reconocido.");
        }
        else {
            if (mensaje.trim().isEmpty()) {
                enviarMensaje("Sistema: No puedes enviar un mensaje vacío.");
                return;
            }
            Mensaje.procesar(mensaje, this, servidor);
        }
    }

    private void manejarCrearGrupo(String mensaje) throws IOException {
        String[] partes = mensaje.split(" ", 2);
        if (partes.length != 2 || partes[1].trim().isEmpty()) {
            enviarMensaje("Sistema: Uso incorrecto. /gcreate <nombre_grupo>");
            return;
        }
        String groupName = partes[1].trim();
        if (groupName.equalsIgnoreCase(BDusuarios.NOMBRE_TODOS)) {
            enviarMensaje("Sistema: No puedes crear un grupo llamado '" + BDusuarios.NOMBRE_TODOS + "'.");
            return;
        }

        if (BDusuarios.crearGrupo(groupName, this.nombreCliente)) {
            enviarMensaje("Sistema: Grupo '" + groupName + "' creado.");
            manejarUnirseGrupo("/join " + groupName);
        } else {
            enviarMensaje("Sistema: Error al crear el grupo (quizás ya existe).");
        }
    }

    private void manejarEliminarGrupo(String mensaje) throws IOException {
        String[] partes = mensaje.split(" ", 2);
        if (partes.length != 2 || partes[1].trim().isEmpty()) {
            enviarMensaje("Sistema: Uso incorrecto. /gdelete <nombre_grupo>");
            return;
        }
        String groupName = partes[1].trim();
        if (groupName.equalsIgnoreCase(BDusuarios.NOMBRE_TODOS)) {
            enviarMensaje("Sistema: No puedes eliminar el grupo '" + BDusuarios.NOMBRE_TODOS + "'.");
            return;
        }

        if (BDusuarios.eliminarGrupo(groupName)) {
            enviarMensaje("Sistema: Grupo '" + groupName + "' eliminado.");
            for (UnCliente cliente : servidor.getTodosLosClientes()) {
                if (cliente.getCurrentGroupName().equalsIgnoreCase(groupName)) {
                    cliente.setCurrentGroup(BDusuarios.ID_TODOS, BDusuarios.NOMBRE_TODOS);
                    cliente.enviarMensaje("Sistema: El grupo '" + groupName + "' fue eliminado. Has sido movido a '" + BDusuarios.NOMBRE_TODOS + "'.");
                    cliente.enviarMensajesPendientes();
                }
            }
        } else {
            enviarMensaje("Sistema: Error al eliminar el grupo (quizás no existe).");
        }
    }

    private void manejarUnirseGrupo(String mensaje) throws IOException {
        String[] partes = mensaje.split(" ", 2);
        if (partes.length != 2 || partes[1].trim().isEmpty()) {
            enviarMensaje("Sistema: Uso incorrecto. /join <nombre_grupo>");
            return;
        }
        String groupName = partes[1].trim();
        int groupId = BDusuarios.obtenerGrupoIdPorNombre(groupName);

        if (groupId == -1) {
            enviarMensaje("Sistema: El grupo '" + groupName + "' no existe.");
            return;
        }

        if (this.currentGroupId == groupId) {
            enviarMensaje("Sistema: Ya estás en el grupo '" + groupName + "'.");
            return;
        }

        if (groupId != BDusuarios.ID_TODOS) {
            BDusuarios.unirUsuarioAGrupo(this.nombreCliente, groupId);
        }

        this.setCurrentGroup(groupId, groupName);
        enviarMensaje("Sistema: Te has unido al grupo '" + groupName + "'.");

        enviarMensajesPendientes();
    }

    private void manejarListarGrupos() throws IOException {
        List<String> grupos = BDusuarios.obtenerTodosLosGrupos();
        if (grupos.isEmpty()) {
            enviarMensaje("Sistema: No hay grupos disponibles.");
            return;
        }
        StringBuilder sb = new StringBuilder("--- GRUPOS DISPONIBLES ---\n");
        for (String grupo : grupos) {
            sb.append("- ").append(grupo).append("\n");
        }
        sb.append("----------------------------");
        enviarMensaje(sb.toString());
    }


    private void manejarRanking() throws IOException {
        List<RankingEntry> ranking = BDusuarios.obtenerRankingGeneral();

        if (ranking.isEmpty()) {
            enviarMensaje("Sistema Ranking: No hay datos de ranking disponibles.");
            return;
        }

        StringBuilder sb = new StringBuilder("\n--- RANKING GATO ---\n");
        sb.append(String.format("%-4s %-15s %-7s %-7s %-7s %s\n", "POS", "USUARIO", "PTS", "V", "E", "D"));
        sb.append("---------------------------------------------------\n");

        int limite = Math.min(ranking.size(), 10);
        for (int i = 0; i < limite; i++) {
            RankingEntry entry = ranking.get(i);
            sb.append(String.format("%-4d %-15s %-7d %-7d %-7d %d\n",
                    i + 1,
                    entry.username,
                    entry.points,
                    entry.victories,
                    entry.ties,
                    entry.defeats));
        }
        sb.append("---------------------------------------------------\n");
        enviarMensaje(sb.toString());
    }

    private void manejarVs(String[] partes) throws IOException {
        if (partes.length != 3) {
            enviarMensaje("Sistema VS: Uso incorrecto. Usa: /vs <usuario1> <usuario2>");
            return;
        }

        String user1 = partes[1].trim();
        String user2 = partes[2].trim();

        if (user1.equalsIgnoreCase(user2)) {
            enviarMensaje("Sistema VS: Los usuarios deben ser diferentes.");
            return;
        }

        if (!BDusuarios.UsuarioExistente(user1) || !BDusuarios.UsuarioExistente(user2)) {
            enviarMensaje("Sistema VS: Asegúrate de que ambos usuarios existan.");
            return;
        }

        Map<String, Integer> stats = BDusuarios.obtenerEstadisticasVs(user1, user2);
        int total = stats.get("total");

        if (total == 0) {
            enviarMensaje("Sistema VS: Nunca han jugado " + user1 + " contra " + user2 + ".");
            return;
        }

        int user1Wins = stats.get(user1 + "_wins");
        int user2Wins = stats.get(user2 + "_wins");
        int ties = stats.get("ties");

        double user1WinRate = (double) user1Wins / total * 100;
        double user2WinRate = (double) user2Wins / total * 100;

        StringBuilder sb = new StringBuilder("\n--- ESTADÍSTICAS VS: " + user1 + " vs " + user2 + " ---\n");
        sb.append(String.format("Total Partidas: %d\n", total));
        sb.append("---------------------------------------------------\n");
        sb.append(String.format("%-15s | %-15s\n", user1, user2));
        sb.append("---------------------------------------------------\n");
        sb.append(String.format("%-15s | %-15s\n", user1Wins + " Victorias", user2Wins + " Victorias"));
        sb.append(String.format("%-15s | %-15s\n", String.format("%.2f%%", user1WinRate), String.format("%.2f%%", user2WinRate)));
        sb.append(String.format("Empates: %d\n", ties));
        sb.append("---------------------------------------------------\n");

        enviarMensaje(sb.toString());
    }


    @Override
    public void run() {
        try {
            inicializarCliente();
            enviarMensajesDeBienvenida();
            bucleDeLectura();
        } catch (SocketException e) {
            System.out.println(nombreCliente + " se ha desconectado (SocketException).");
        } catch (IOException ex) {
            manejarErrorIO();
        } finally {
            manejarDesconexion();
        }
    }

    public void postAutenticacionExitosa() throws IOException {
        this.setCurrentGroup(BDusuarios.ID_TODOS, BDusuarios.NOMBRE_TODOS);
        enviarMensaje("Sistema: Has iniciado sesión. Estás en el grupo '" + this.currentGroupName + "'.");
        enviarMensajesPendientes();
    }

}