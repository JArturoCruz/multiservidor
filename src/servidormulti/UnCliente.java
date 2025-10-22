package servidormulti;
import mensaje.Mensaje;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

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

    UnCliente(Socket s, ServidorMulti servidor) throws IOException {
        salida = new DataOutputStream(s.getOutputStream());
        entrada = new DataInputStream(s.getInputStream());
        this.servidor = servidor;
        this.autenticador = new AutenticadorCliente(this, servidor);
        this.controladorInvitado = new ControladorMensajesInvitado(this);
        this.controladorBloqueo = new ControladorBloqueo(this);
        this.controladorJuego = servidor.getControladorJuego();
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

        String oponenteNombre = controladorJuego.getOponenteSiEstaJugando(nombreCliente);

        if (oponenteNombre != null) {
            if (mensaje.startsWith("/") || mensaje.startsWith("@")) {
                enviarMensaje("Sistema: Estás en una partida de Gato. Solo se permite chat simple con el oponente o el comando /move.");
                return;
            }

            Mensaje.enviarMensajePrivadoEntreJugadores(mensaje, this, oponenteNombre, servidor);
            return;
        }

        if (mensaje.startsWith("/")) {
            enviarMensaje("Sistema: Comando no reconocido o reservado.");
            return;
        }

        if (!mensaje.startsWith("@") && mensaje.trim().isEmpty()) {
            enviarMensaje("Sistema: No puedes enviar un mensaje público vacío.");
        } else {
            Mensaje.procesar(mensaje, this, servidor);
        }
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
        enviarMensaje("Sistema: Juega al Gato con usuarios autenticados:");
        enviarMensaje("Sistema: - Proponer juego: /gato <usuario>");
        enviarMensaje("Sistema: - Responder propuesta: /accept <usuario> o /reject <usuario>");
        enviarMensaje("Sistema: - Mover: /move <fila> <columna> (ej: /move 1 3)");
    }

    private void bucleDeLectura() throws IOException {
        while (true) {
            String mensaje = entrada.readUTF();

            String comando = mensaje.split(" ", 2)[0];
            boolean esComandoJuegoRespuesta = comando.equals("/accept") || comando.equals("/reject");

            if (autenticado && controladorJuego.tieneInvitacionPendiente(nombreCliente)) {
                if (esComandoJuegoRespuesta) {
                    controladorJuego.manejarComando(mensaje, this);
                } else {
                    enviarMensaje("Sistema: Tienes una invitación pendiente para jugar al Gato. Debes usar /accept <usuario> o /reject <usuario> para responder antes de realizar cualquier otra acción.");
                }
                continue;
            }

            if (comando.equals("/gato") || comando.equals("/move") || esComandoJuegoRespuesta) {
                controladorJuego.manejarComando(mensaje, this);
                continue;
            }

            if (comando.equals("/block") || comando.equals("/unblock")) {
                controladorBloqueo.manejarComando(mensaje);
                continue;
            }

            if (comando.equals("/register") || comando.equals("/login")) {
                autenticador.manejarAutenticacion(mensaje);
                continue;
            }

            if (autenticado) {
                manejarMensajeAutenticado(mensaje);
            } else {
                controladorInvitado.manejarMensaje(mensaje, servidor);
            }
        }
    }

    private void manejarDesconexion() {
        if (nombreCliente != null) {
            servidor.removerCliente(nombreCliente);
            Mensaje.notificarATodos(nombreCliente + " ha abandonado el chat.", null, servidor);
        }
    }

    private void manejarErrorIO() {
        System.out.println("Error de comunicación con " + (nombreCliente != null ? nombreCliente : "un cliente"));
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