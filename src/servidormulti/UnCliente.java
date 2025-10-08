package servidormulti;
import mensaje.Mensaje;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class UnCliente implements Runnable {

    final DataOutputStream salida;
    final DataInputStream entrada;
    private String nombreCliente;
    private int mensajesGratisEnviados = 0; // Nuevo contador de mensajes enviados
    private boolean autenticado = false;    // Nuevo estado de autenticación
    private static final int LIMITE_MENSAJES_GRATIS = 3;

    UnCliente(Socket s) throws IOException {
        salida = new DataOutputStream(s.getOutputStream());
        entrada = new DataInputStream(s.getInputStream());
    }

    public String getNombreCliente() {
        return nombreCliente;
    }

    public void enviarMensaje(String mensaje) throws IOException {
        this.salida.writeUTF(mensaje);
    }

    private void manejarAutenticacion(String comando) throws IOException {
        if (!autenticado) {
            autenticado = true;
            enviarMensaje("Sistema: Autenticación (" + comando + ") exitosa. Ahora puedes enviar mensajes sin límite.");
            System.out.println(nombreCliente + " se ha autenticado.");
        } else {
            enviarMensaje("Sistema: Ya estás autenticado.");
        }
    }

    @Override
    public void run() {
        try {
            nombreCliente = entrada.readUTF();
            ServidorMulti.clientes.put(nombreCliente, this);
            Mensaje.notificarATodos(nombreCliente + " se ha unido al chat como invitado.", this);

            enviarMensaje("Sistema: Tienes un límite de " + LIMITE_MENSAJES_GRATIS + " mensajes antes de necesitar autenticarte.");
            enviarMensaje("Sistema: Usa '/login' o '/register' para autenticarte.");

            while (true) {
                String mensaje = entrada.readUTF();

                if (mensaje.startsWith("/")) {
                    if (mensaje.equals("/login") || mensaje.equals("/register")) {
                        manejarAutenticacion(mensaje);
                    } else {
                        enviarMensaje("Sistema: Comando no reconocido. Usa '/login' o '/register' para autenticarte.");
                    }
                    continue;
                }

                if (autenticado) {
                    Mensaje.procesar(mensaje, this);
                } else {
                    if (mensajesGratisEnviados < LIMITE_MENSAJES_GRATIS) {
                        Mensaje.procesar(mensaje, this);
                        mensajesGratisEnviados++;
                        int restantes = LIMITE_MENSAJES_GRATIS - mensajesGratisEnviados;
                        if (restantes > 0) {
                            enviarMensaje("Sistema: Mensaje enviado. Te quedan " + restantes + " mensajes gratis.");
                        } else {
                            enviarMensaje("Sistema: ¡ATENCIÓN! Has agotado tus mensajes gratis (" + LIMITE_MENSAJES_GRATIS + "). Por favor, usa '/login' o '/register' para continuar enviando.");
                        }
                    } else {
                        // Límite excedido: bloquea el envío y avisa
                        enviarMensaje("Sistema: No puedes enviar más mensajes. Debes usar '/login' o '/register' para continuar enviando.");
                    }
                }
            }
        } catch (SocketException e) {
            if (nombreCliente != null) {
                ServidorMulti.clientes.remove(nombreCliente);
                Mensaje.notificarATodos(nombreCliente + " ha abandonado el chat.", null);
            }
        } catch (IOException ex) {
            System.out.println("Error de comunicación con " + (nombreCliente != null ? nombreCliente : "un cliente"));
            if (nombreCliente != null) {
                ServidorMulti.clientes.remove(nombreCliente);
                Mensaje.notificarATodos(nombreCliente + " ha abandonado el chat.", null);
            }
        }
    }
}