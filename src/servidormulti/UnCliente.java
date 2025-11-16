package servidormulti;
import bd.RGrupos;
import mensaje.Mensaje;
import servidormulti.estado.EstadoAutenticado;
import servidormulti.estado.EstadoInvitado;
import servidormulti.estado.EstadoCliente;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;

public class UnCliente implements Runnable {

    final DataOutputStream salida;
    final DataInputStream entrada;
    private String nombreCliente;
    private int mensajesGratisEnviados = 0;
    public static final int LIMITE_MENSAJES_GRATIS = 3;

    private int currentGroupId;
    private String currentGroupName;

    private final ServidorMulti servidor;
    private final AutenticadorCliente autenticador;
    private final ControladorJuego controladorJuego;
    private final FormateadorMensajes formateador;

    private EstadoCliente estadoActual;

    UnCliente(Socket s, ServidorMulti servidor) throws IOException {
        salida = new DataOutputStream(s.getOutputStream());
        entrada = new DataInputStream(s.getInputStream());
        this.servidor = servidor;
        this.autenticador = new AutenticadorCliente(this, servidor);
        this.controladorJuego = servidor.getControladorJuego();
        this.formateador = new FormateadorMensajes();
        this.estadoActual = new EstadoInvitado(this);
        this.currentGroupId = RGrupos.ID_TODOS;
        this.currentGroupName = RGrupos.NOMBRE_TODOS;
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

        if (comando.equals("/register") || comando.equals("/login")) {
            autenticador.manejarAutenticacion(mensaje);
        } else {
            estadoActual.procesarMensaje(mensaje);
        }
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
        Mensaje.notificarATodos(msg, null, servidor);
    }

    public void enviarMensajesPendientes() throws IOException {
        if (!isAutenticado()) return;
        List<RGrupos.MensajeGrupo> mensajes = RGrupos.obtenerMensajesNoVistos(this.nombreCliente, this.currentGroupId);

        if (mensajes.isEmpty()) {
            enviarMensaje("Sistema: No hay mensajes nuevos en '" + this.currentGroupName + "'.");
            return;
        }

        enviarMensaje("Sistema: --- Mostrando mensajes no leídos para '" + this.currentGroupName + "' ---");
        long ultimoId = 0;
        for (RGrupos.MensajeGrupo msg : mensajes) {
            enviarMensaje("[" + msg.timestamp + "] " + msg.sender + ": " + msg.content);
            ultimoId = msg.messageId;
        }

        if (ultimoId > 0) {
            RGrupos.actualizarUltimoMensajeVisto(this.nombreCliente, this.currentGroupId, ultimoId);
        }
        enviarMensaje("Sistema: --- Fin de mensajes no leídos ---");
    }

    public void setEstadoAutenticado() {
        this.estadoActual = new EstadoAutenticado(this, servidor);
        resetMensajesGratisEnviados();
    }

    public void setEstadoInvitado() throws IOException {
        String nombreAnterior = this.nombreCliente;

        controladorJuego.finalizarPorDesconexion(this);
        String msg = nombreAnterior + " ha cerrado sesión.";
        System.out.println(msg);
        Mensaje.notificarATodos(msg, null, servidor); // Notifica a otros clientes

        servidor.removerCliente(nombreAnterior);

        this.nombreCliente = servidor.generarNombreAnonimo();
        servidor.agregarCliente(nombreCliente, this);

        this.estadoActual = new EstadoInvitado(this);
        this.currentGroupId = RGrupos.ID_TODOS;
        this.currentGroupName = RGrupos.NOMBRE_TODOS;
        resetMensajesGratisEnviados();

        enviarMensaje("Sistema: Sesión cerrada con éxito. Ahora eres un invitado con el nombre " + this.nombreCliente + ".");
        enviarMensajesDeBienvenida();
    }

    public void enviarMensaje(String mensaje) throws IOException { this.salida.writeUTF(mensaje); }
    public String getNombreCliente() { return nombreCliente; }
    public void setNombreCliente(String n) { this.nombreCliente = n; }
    public boolean isAutenticado() { return this.estadoActual instanceof EstadoAutenticado; }
    public int getMensajesGratisEnviados() { return mensajesGratisEnviados; }
    public void incrementarMensajesGratisEnviados() { this.mensajesGratisEnviados++; }
    public void resetMensajesGratisEnviados() { this.mensajesGratisEnviados = 0; }
    public int getCurrentGroupId() { return currentGroupId; }
    public String getCurrentGroupName() { return currentGroupName; }
    public void setCurrentGroup(int id, String name) { this.currentGroupId = id; this.currentGroupName = name; }
    public ServidorMulti getServidor() { return servidor; }
}