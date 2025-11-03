package servidormulti;
import mensaje.Mensaje;
import bd.BDusuarios;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Set;

public class UnCliente implements Runnable {

    final DataOutputStream salida;
    final DataInputStream entrada;
    private String nombreCliente;
    private int mensajesGratisEnviados = 0;
    private boolean autenticado = false;
    public static final int LIMITE_MENSAJES_GRATIS = 3;

    private int currentGroupId;
    private String currentGroupName;

    private final ServidorMulti servidor;
    private final AutenticadorCliente autenticador;
    private final ControladorBloqueo controladorBloqueo;
    private final ControladorJuego controladorJuego;
    private final ManejadorComandosJuego manejadorComandosJuego;
    private final ManejadorComandosUsuario manejadorUsuario;
    private final FormateadorMensajes formateador;

    UnCliente(Socket s, ServidorMulti servidor) throws IOException {
        salida = new DataOutputStream(s.getOutputStream());
        entrada = new DataInputStream(s.getInputStream());
        this.servidor = servidor;
        this.autenticador = new AutenticadorCliente(this, servidor);
        this.controladorBloqueo = new ControladorBloqueo(this);
        this.controladorJuego = servidor.getControladorJuego();
        this.manejadorComandosJuego = new ManejadorComandosJuego(this.controladorJuego, servidor.getGestorPropuestas());
        this.manejadorUsuario = new ManejadorComandosUsuario(this, servidor);
        this.formateador = new FormateadorMensajes();
        this.currentGroupId = BDusuarios.ID_TODOS;
        this.currentGroupName = BDusuarios.NOMBRE_TODOS;
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
            System.out.println("Error de comunicación con " + (nombreCliente != null ? nombreCliente : "un cliente"));
        } finally {
            manejarDesconexion();
        }
    }

    private void inicializarCliente() throws IOException {
        nombreCliente = servidor.generarNombreAnonimo();
        servidor.agregarCliente(nombreCliente, this);
        Mensaje.notificarATodos(nombreCliente + " se ha unido al chat (en " + this.currentGroupName + ").", this, servidor);
    }

    private void enviarMensajesDeBienvenida() throws IOException {
        String[] bienvenida = formateador.formatearBienvenida();
        for (String linea : bienvenida) {
            enviarMensaje(linea);
        }
    }

    private void bucleDeLectura() throws IOException {
        while (true) {
            String mensaje = entrada.readUTF();
            procesarMensaje(mensaje);
        }
    }

    private void procesarMensaje(String mensaje) throws IOException {
        String comando = mensaje.split(" ", 2)[0].toLowerCase();

        if (esComandoAutenticacion(comando)) {
            autenticador.manejarAutenticacion(mensaje);
        } else if (autenticado) {
            procesarMensajeAutenticado(mensaje, comando);
        } else {
            manejarMensajeInvitado(mensaje);
        }
    }

    private void procesarMensajeAutenticado(String mensaje, String comando) throws IOException {
        if (estaEnInteraccionJuego()) {
            manejarMensajeEnJuego(mensaje, comando);
            return;
        }

        if (esComandoJuego(comando)) {
            if (validarGrupoParaJuego(mensaje, comando)) {
                manejadorComandosJuego.manejarComando(mensaje, this);
            }
        } else if (esComandoUsuario(comando)) {
            manejadorUsuario.manejar(mensaje, comando);
        } else if (esComandoBloqueo(comando)) {
            controladorBloqueo.manejarComando(mensaje);
        } else if (comando.startsWith("/")) {
            enviarMensaje("Sistema: Comando no reconocido.");
        } else {
            Mensaje.procesar(mensaje, this, servidor);
        }
    }

    private void manejarMensajeInvitado(String mensaje) throws IOException {
        if (mensajesGratisEnviados < LIMITE_MENSAJES_GRATIS) {
            if (mensaje.startsWith("/") || mensaje.startsWith("@")) {
                enviarMensaje("Sistema: Los invitados no pueden usar comandos ni mensajes privados. Registre una cuenta.");
            } else if (!mensaje.trim().isEmpty()) {
                Mensaje.procesar(mensaje, this, servidor);
                mensajesGratisEnviados++;
            }
        } else {
            enviarMensaje("Sistema: Límite de mensajes gratis alcanzado. Por favor, regístrate o inicia sesión.");
        }
    }

    private void manejarMensajeEnJuego(String mensaje, String comando) throws IOException {
        if (esComandoJuego(comando) || esComandoEstadisticas(comando)) {
            manejadorComandosJuego.manejarComando(mensaje, this);
        } else if (comando.equals("/ranking") || comando.equals("/vs")) {
            manejadorUsuario.manejar(mensaje, comando);
        } else if (mensaje.startsWith("@")) {
            manejarChatPrivadoJuego(mensaje);
        } else {
            enviarMensaje("Sistema: Chat público bloqueado. Usa @<oponente> <mensaje> o comandos de juego.");
        }
    }

    private void manejarChatPrivadoJuego(String mensaje) throws IOException {
        String[] partes = mensaje.split(" ", 2);
        String nombreDestino = partes[0].substring(1);
        Set<String> oponentes = controladorJuego.getOponentesActivos(nombreCliente);

        if (oponentes.contains(nombreDestino)) {
            Mensaje.enviarMensajePrivadoEntreJugadores(mensaje, this, nombreDestino, servidor);
        } else {
            enviarMensaje("Sistema: Solo puedes enviar mensajes privados a tus oponentes activos.");
        }
    }

    private boolean validarGrupoParaJuego(String mensaje, String comando) throws IOException {
        if (!comando.equals("/gato") && !comando.equals("/accept")) {
            return true;
        }

        String oponenteNombre = obtenerOponenteDeComando(mensaje);
        if (oponenteNombre == null) {
            enviarMensaje("Sistema: Formato incorrecto. Uso: " + comando + " <usuario>");
            return false;
        }

        UnCliente oponente = servidor.getCliente(oponenteNombre);
        if (oponente == null) {
            enviarMensaje("Sistema: El usuario '" + oponenteNombre + "' no está conectado.");
            return false;
        }

        if (this.currentGroupId != oponente.getCurrentGroupId()) {
            enviarMensaje("Sistema: Solo puedes jugar con usuarios en tu mismo grupo (" + this.currentGroupName + ").");
            enviarMensaje("Sistema: '" + oponenteNombre + "' está en el grupo '" + oponente.getCurrentGroupName() + "'.");
            return false;
        }
        return true;
    }

    private void manejarDesconexion() {
        if (nombreCliente == null) return;
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

    public void enviarMensajesPendientes() throws IOException {
        if (!autenticado) return;
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

    public void postAutenticacionExitosa() throws IOException {
        this.setCurrentGroup(BDusuarios.ID_TODOS, BDusuarios.NOMBRE_TODOS);
        enviarMensaje("Sistema: Has iniciado sesión. Estás en el grupo '" + this.currentGroupName + "'.");
        enviarMensajesPendientes();
    }

    public void enviarMensaje(String mensaje) throws IOException { this.salida.writeUTF(mensaje); }
    public String getNombreCliente() { return nombreCliente; }
    public void setNombreCliente(String n) { this.nombreCliente = n; }
    public void setAutenticado(boolean a) { this.autenticado = a; }
    public boolean isAutenticado() { return autenticado; }
    public void resetMensajesGratisEnviados() { this.mensajesGratisEnviados = 0; }
    public int getCurrentGroupId() { return currentGroupId; }
    public String getCurrentGroupName() { return currentGroupName; }
    public void setCurrentGroup(int id, String name) { this.currentGroupId = id; this.currentGroupName = name; }

    private boolean estaEnInteraccionJuego() {
        return controladorJuego.estaJugando(nombreCliente) || controladorJuego.tieneRevanchaPendiente(nombreCliente);
    }
    private String obtenerOponenteDeComando(String mensaje) {
        String[] partes = mensaje.split(" ", 3);
        return (partes.length >= 2) ? partes[1].trim() : null;
    }
    private boolean esComandoAutenticacion(String c) { return c.equals("/register") || c.equals("/login"); }
    private boolean esComandoJuego(String c) { return c.equals("/move") || c.equals("/gato") || c.equals("/accept") || c.equals("/reject") || c.equals("/si") || c.equals("/no"); }
    private boolean esComandoEstadisticas(String c) { return c.equals("/ranking") || c.equals("/vs"); }
    private boolean esComandoGrupo(String c) { return c.equals("/gcreate") || c.equals("/gdelete") || c.equals("/join") || c.equals("/glist"); }
    private boolean esComandoUsuario(String c) { return esComandoGrupo(c) || esComandoEstadisticas(c); }
    private boolean esComandoBloqueo(String c) { return c.equals("/block") || c.equals("/unblock"); }
}