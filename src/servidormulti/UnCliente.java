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

    private final AutenticadorCliente autenticador;
    private final ControladorMensajesInvitado controladorInvitado;

    UnCliente(Socket s) throws IOException {
        salida = new DataOutputStream(s.getOutputStream());
        entrada = new DataInputStream(s.getInputStream());
        this.autenticador = new AutenticadorCliente(this);
        this.controladorInvitado = new ControladorMensajesInvitado(this);
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
        if (!mensaje.startsWith("@") && mensaje.trim().isEmpty()) {
            enviarMensaje("Sistema: No puedes enviar un mensaje público vacío.");
        } else {
            Mensaje.procesar(mensaje, this);
        }
    }

    private void inicializarCliente() throws IOException {
        synchronized (ServidorMulti.clientes) {
            ServidorMulti.anonimoCONT++;
            nombreCliente = "anonimo" + ServidorMulti.anonimoCONT;
        }

        ServidorMulti.clientes.put(nombreCliente, this);
        Mensaje.notificarATodos(nombreCliente + " se ha unido al chat como invitado.", this);
    }

    private void enviarMensajesDeBienvenida() throws IOException {
        enviarMensaje("Sistema: Tu nombre actual es " + nombreCliente + ". Tienes un límite de " + LIMITE_MENSAJES_GRATIS + " mensajes antes de autenticarte.");
        enviarMensaje("Sistema: Usa '/register <nombre_usuario> <PIN>' (Ej: /register Arturo 1234) o '/login <nombre_usuario> <PIN>'.");
    }

    private void bucleDeLectura() throws IOException {
        while (true) {
            String mensaje = entrada.readUTF();

            if (mensaje.startsWith("/register") || mensaje.startsWith("/login")) {
                autenticador.manejarAutenticacion(mensaje);
                continue;
            }

            if (autenticado) {
                manejarMensajeAutenticado(mensaje);
            } else {
                controladorInvitado.manejarMensaje(mensaje);
            }
        }
    }

    private void manejarDesconexion() {
        if (nombreCliente != null) {
            ServidorMulti.clientes.remove(nombreCliente);
            Mensaje.notificarATodos(nombreCliente + " ha abandonado el chat.", null);
        }
    }

    private void manejarErrorIO() {
        System.out.println("Error de comunicación con " + (nombreCliente != null ? nombreCliente : "un cliente"));
        if (nombreCliente != null) {
            ServidorMulti.clientes.remove(nombreCliente);
            Mensaje.notificarATodos(nombreCliente + " ha abandonado el chat.", null);
        }
    }

    @Override
    public void run() {
        try {
            inicializarCliente();
            enviarMensajesDeBienvenida();
            bucleDeLectura();
        } catch (SocketException e) {
            manejarDesconexion();
        } catch (IOException ex) {
            manejarErrorIO();
        }
    }
}