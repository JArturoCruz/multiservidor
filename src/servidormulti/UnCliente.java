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

    private final ServidorMulti servidor;

    private final AutenticadorCliente autenticador;
    private final ControladorMensajesInvitado controladorInvitado;
    private final ControladorBloqueo controladorBloqueo;
    private final ControladorJuego controladorJuego;
    private final ManejadorComandosJuego manejadorComandosJuego;

    UnCliente(Socket s, ServidorMulti servidor) throws IOException {
        salida = new DataOutputStream(s.getOutputStream());
        entrada = new DataInputStream(s.getInputStream());
        this.servidor = servidor;
        this.autenticador = new AutenticadorCliente(this, servidor);
        this.controladorInvitado = new ControladorMensajesInvitado(this);
        this.controladorBloqueo = new ControladorBloqueo(this);
        this.controladorJuego = servidor.getControladorJuego();
        this.manejadorComandosJuego = new ManejadorComandosJuego(this.controladorJuego, servidor.getGestorPropuestas());
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

    private void manejarMensajeAutenticado(String mensaje) throws IOException {
        if (mensaje.trim().isEmpty()) {
            enviarMensaje("Sistema: No puedes enviar un mensaje vacío.");
            return;
        }

        if (mensaje.startsWith("/")) {
            enviarMensaje("Sistema: Comando no reconocido o reservado.");
            return;
        }

        Mensaje.procesar(mensaje, this, servidor);
    }

    private void inicializarCliente() throws IOException {
        nombreCliente = servidor.generarNombreAnonimo();
        servidor.agregarCliente(nombreCliente, this);
        Mensaje.notificarATodos(nombreCliente + " se ha unido al chat como invitado.", this, servidor);
    }

    private void enviarMensajesDeBienvenida() throws IOException {
        enviarMensaje("Sistema: Tu nombre actual es " + nombreCliente + ". Tienes un límite de " + LIMITE_MENSAJES_GRATIS + " mensajes antes de autenticarte.");
        enviarMensaje("Sistema: Usa '/register <nombre_usuario> <PIN>' (Ej: /register Arturo 1234) o '/login <nombre_usuario> <PIN>'.");
        enviarMensaje("Sistema: Usa '/block <usuario>' y '/unblock <usuario>' para gestionar bloqueos (requiere estar autenticado).");
        enviarMensaje("Sistema: Juega al Gato con usuarios autenticados. Puedes tener múltiples partidas activas:");
        enviarMensaje("Sistema: - Proponer juego: /gato <usuario>");
        enviarMensaje("Sistema: - Responder propuesta: /accept <proponente> o /reject <proponente>");
        enviarMensaje("Sistema: - Mover: /move <oponente> <fila> <columna> (ej: /move [NombreOponente] 1 3)");
        enviarMensaje("Sistema: - Revancha: /si <oponente> o /no <oponente> (al finalizar un juego).");
        enviarMensaje("Sistema: - Ranking: /ranking (Muestra los 10 mejores jugadores)");
        enviarMensaje("Sistema: - Historial VS: /vs <usuario1> <usuario2> (Muestra estadísticas entre dos jugadores)");
    }

    private void manejarDesconexion() {
        if (nombreCliente != null) {
            try {
                controladorJuego.finalizarPorDesconexion(this);
            } catch (IOException e) {
                System.out.println("Error al notificar desconexión de juego.");
            }
            servidor.removerCliente(nombreCliente);
            Mensaje.notificarATodos(nombreCliente + " ha abandonado el chat.", null, servidor);
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
        String comando = mensaje.split(" ", 2)[0];

        if (comando.equals("/register") || comando.equals("/login")) {
            autenticador.manejarAutenticacion(mensaje);
        } else if (autenticado) {
            procesarMensajeAutenticado(mensaje, comando);
        } else {
            controladorInvitado.manejarMensaje(mensaje, servidor);
        }
    }

    private void procesarMensajeAutenticado(String mensaje, String comando) throws IOException {

        boolean estaEnInteraccionJuego = controladorJuego.estaJugando(nombreCliente) || controladorJuego.tieneRevanchaPendiente(nombreCliente);

        if (comando.equals("/move") || comando.equals("/gato") || comando.equals("/accept") || comando.equals("/reject") || comando.equals("/si") || comando.equals("/no")) {
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
                enviarMensaje("Sistema: Chat público bloqueado. Usa @<oponente> <mensaje> para hablar con tu oponente.");
            }
        }
        else if (comando.equals("/block") || comando.equals("/unblock")) {
            controladorBloqueo.manejarComando(mensaje);
        }
        else if (comando.startsWith("/")) {
            enviarMensaje("Sistema: Comando no reconocido.");
        }
        else {
            manejarMensajeAutenticado(mensaje);
        }
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
}