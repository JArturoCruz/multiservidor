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

    private void manejarAutenticacion(String comandoCompleto) throws IOException {
        String[] partes = comandoCompleto.split(" ");
        String comando = partes[0];

        if (partes.length != 2) {
            enviarMensaje("Sistema: Formato incorrecto. Uso: " + comando + " <PIN de 4 dígitos>");
            return;
        }

        String pin = partes[1];

        if (!pin.matches("\\d{4}")) {
            enviarMensaje("Sistema: El PIN debe ser de 4 dígitos numéricos.");
            return;
        }

        if (autenticado) {
            enviarMensaje("Sistema: Ya estás autenticado. No es necesario realizar '" + comando + "'.");
            return;
        }

        if (comando.equals("/register")) {
            if (BDusuarios.UsuarioExistente(nombreCliente)) {
                enviarMensaje("Sistema: Error al registrar. El usuario '" + nombreCliente + "' ya existe. Por favor, usa /login.");
            } else {
                if (BDusuarios.RegistrarUsuario(nombreCliente, pin)) {
                    autenticado = true;
                    mensajesGratisEnviados = 0;
                    enviarMensaje("Sistema: ¡Registro exitoso! Ahora estás autenticado y puedes enviar mensajes ilimitados.");
                    Mensaje.notificarATodos(nombreCliente + " ha completado su registro y ahora es un usuario verificado.", this);
                } else {
                    enviarMensaje("Sistema: Error desconocido al registrar. Intenta de nuevo.");
                }
            }

        } else if (comando.equals("/login")) {
            if (!BDusuarios.UsuarioExistente(nombreCliente)) {
                enviarMensaje("Sistema: Error al iniciar sesión. El usuario '" + nombreCliente + "' no está registrado. Por favor, usa /register.");
            } else {
                if (BDusuarios.AutenticarUsuario(nombreCliente, pin)) {
                    autenticado = true;
                    mensajesGratisEnviados = 0;
                    enviarMensaje("Sistema: ¡Inicio de sesión exitoso! Ahora estás autenticado y puedes enviar mensajes ilimitados.");
                    Mensaje.notificarATodos(nombreCliente + " ha iniciado sesión.", this);
                } else {
                    enviarMensaje("Sistema: PIN incorrecto para el usuario '" + nombreCliente + "'.");
                }
            }
        }
    }

    @Override
    public void run() {
        try {
            nombreCliente = entrada.readUTF();

            ServidorMulti.clientes.put(nombreCliente, this);

            Mensaje.notificarATodos(nombreCliente + " se ha unido al chat como invitado.", this);

            enviarMensaje("Sistema: Tienes un límite de " + LIMITE_MENSAJES_GRATIS + " mensajes antes de necesitar autenticarte.");
            enviarMensaje("Sistema: Tu nombre de usuario es '" + nombreCliente + "'.");
            enviarMensaje("Sistema: Usa '/register <PIN>' (ej: /register 1234) o '/login <PIN>' para autenticarte.");

            while (true) {
                String mensaje = entrada.readUTF();

                if (mensaje.startsWith("/register") || mensaje.startsWith("/login")) {
                    manejarAutenticacion(mensaje);
                    continue;
                }

                if (autenticado) {
                    if (!mensaje.startsWith("@") && mensaje.trim().isEmpty()) {
                        enviarMensaje("Sistema: No puedes enviar un mensaje público vacío.");
                    } else {
                        Mensaje.procesar(mensaje, this);
                    }
                } else {
                    if (mensajesGratisEnviados < LIMITE_MENSAJES_GRATIS) {

                        boolean mensajeValido = false;

                        if (!mensaje.startsWith("@") && mensaje.trim().isEmpty()) {
                            enviarMensaje("Sistema: No puedes enviar un mensaje público vacío.");
                        } else {
                            mensajeValido = Mensaje.procesar(mensaje, this);
                        }

                        if (mensajeValido) {
                            mensajesGratisEnviados++;
                            int restantes = LIMITE_MENSAJES_GRATIS - mensajesGratisEnviados;
                            if (restantes > 0) {
                                enviarMensaje("Sistema: Mensaje enviado. Te quedan " + restantes + " mensajes gratis.");
                            } else {
                                enviarMensaje("Sistema: ¡ATENCIÓN! Has agotado tus mensajes gratis (" + LIMITE_MENSAJES_GRATIS + "). Por favor, usa '/login <PIN>' o '/register <PIN>' para continuar enviando.");
                            }
                        }
                    } else {
                        enviarMensaje("Sistema: No puedes enviar más mensajes. Debes usar '/login <PIN>' o '/register <PIN>' para continuar enviando.");
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