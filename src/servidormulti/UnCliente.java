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
        String oldNombreCliente = nombreCliente;

        if (partes.length != 3) {
            enviarMensaje("Sistema: Formato incorrecto. Uso: " + comando + " <nombre_usuario> <PIN de 4 dígitos>");
            return;
        }

        String nuevoNombre = partes[1];
        String pin = partes[2];

        if (!pin.matches("\\d{4}")) {
            enviarMensaje("Sistema: El PIN debe ser de 4 dígitos numéricos.");
            return;
        }

        if (autenticado) {
            enviarMensaje("Sistema: Ya estás autenticado. No es necesario realizar '" + comando + "'.");
            return;
        }

        if (nuevoNombre.toLowerCase().startsWith("anonimo") && !nuevoNombre.equals(oldNombreCliente)) {
            enviarMensaje("Sistema: El nombre de usuario '" + nuevoNombre + "' está reservado. Por favor, elige otro.");
            return;
        }

        if (comando.equals("/register")) {
            Register(nuevoNombre, pin, oldNombreCliente);
        } else if (comando.equals("/login")) {
            Login(nuevoNombre, pin, oldNombreCliente);
        }
    }

    private void Register(String nuevoNombre, String pin, String oldNombreCliente) throws IOException {
        if (BDusuarios.UsuarioExistente(nuevoNombre)) {
            enviarMensaje("Sistema: Error al registrar. El usuario '" + nuevoNombre + "' ya existe. Por favor, usa /login.");
        } else {
            if (BDusuarios.RegistrarUsuario(nuevoNombre, pin)) {
                autenticacionExitosa(nuevoNombre, oldNombreCliente, nuevoNombre + " acaba de registrarse");
                enviarMensaje("Sistema: ¡Registro exitoso! Tu nombre ahora es '" + nuevoNombre + "'. Puedes enviar mensajes ilimitados.");
            } else {
                enviarMensaje("Sistema: Error desconocido al registrar. Intenta de nuevo.");
            }
        }
    }

    private void Login(String nuevoNombre, String pin, String oldNombreCliente) throws IOException {
        if (!BDusuarios.UsuarioExistente(nuevoNombre)) {
            enviarMensaje("Sistema: Error al iniciar sesión. El usuario '" + nuevoNombre + "' no está registrado. Por favor, usa /register.");
        } else {
            if (BDusuarios.AutenticarUsuario(nuevoNombre, pin)) {
                autenticacionExitosa(nuevoNombre, oldNombreCliente, nuevoNombre + " ha iniciado sesion");
                enviarMensaje("Sistema: ¡Inicio de sesión exitoso! Tu nombre ahora es '" + nuevoNombre + "'. Puedes enviar mensajes ilimitados.");
            } else {
                enviarMensaje("Sistema: PIN incorrecto para el usuario '" + nuevoNombre + "'.");
            }
        }
    }

    private void autenticacionExitosa(String nuevoNombre, String oldNombreCliente, String notificacion) {
        ServidorMulti.clientes.remove(oldNombreCliente);
        nombreCliente = nuevoNombre; // Establece el nuevo nombre
        ServidorMulti.clientes.put(nombreCliente, this); // Añade con el nuevo nombre

        autenticado = true;
        mensajesGratisEnviados = 0;
        Mensaje.notificarATodos(notificacion, this);
    }

    @Override
    public void run() {
        try {
            synchronized (ServidorMulti.clientes) {
                ServidorMulti.anonimoCONT++;
                nombreCliente = "anonimo" + ServidorMulti.anonimoCONT;
            }

            ServidorMulti.clientes.put(nombreCliente, this);
            Mensaje.notificarATodos(nombreCliente + " se ha unido al chat como invitado.", this);

            enviarMensaje("Sistema: Tu nombre actual es " + nombreCliente + ". Tienes un límite de " + LIMITE_MENSAJES_GRATIS + " mensajes antes de autenticarte.");
            enviarMensaje("Sistema: Usa '/register <nombre_usuario> <PIN>' (Ej: /register Arturo 1234) o '/login <nombre_usuario> <PIN>'.");
            while (true) {
                String mensaje = entrada.readUTF();
                if (mensaje.startsWith("/register") || mensaje.startsWith("/login")) {
                    // Llama al método refactorizado
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
                                enviarMensaje("Sistema: ¡ATENCIÓN! Has agotado tus mensajes gratis (" + LIMITE_MENSAJES_GRATIS + "). Por favor, usa '/login <nombre_usuario> <PIN>' o '/register <nombre_usuario> <PIN>' para continuar enviando.");
                            }
                        }
                    } else {
                        enviarMensaje("Sistema: No puedes enviar más mensajes. Debes usar '/login <nombre_usuario> <PIN>' o '/register <nombre_usuario> <PIN>' para continuar enviando.");
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