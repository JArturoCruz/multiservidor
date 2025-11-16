package servidormulti;

import java.io.IOException;
import bd.RGrupos;
import bd.RUsuarios;
import mensaje.Mensaje;

public class AutenticadorCliente {

    private final UnCliente cliente;
    private final ServidorMulti servidor;

    public AutenticadorCliente(UnCliente cliente, ServidorMulti servidor) {
        this.cliente = cliente;
        this.servidor = servidor;
    }

    public void manejarAutenticacion(String comandoCompleto) throws IOException {
        String[] partes = comandoCompleto.split(" ", 3);
        String comando = partes[0];

        if (!validarArgumentos(partes, comando)) return;

        String nuevoNombre = partes[1];
        String pin = partes[2];
        String oldNombreCliente = cliente.getNombreCliente();

        if (cliente.isAutenticado()) {
            cliente.enviarMensaje("Sistema: Ya estás autenticado.");
            return;
        }
        if (!validarPin(pin) || !validarNombre(nuevoNombre, oldNombreCliente)) return;

        if (comando.equals("/register")) {
            manejarRegistro(nuevoNombre, pin, oldNombreCliente);
        } else if (comando.equals("/login")) {
            manejarInicioSesion(nuevoNombre, pin, oldNombreCliente);
        }
    }

    private boolean validarArgumentos(String[] partes, String comando) throws IOException {
        if (partes.length != 3) {
            cliente.enviarMensaje("Sistema: Formato incorrecto. Uso: " + comando + " <nombre_usuario> <PIN de 4 dígitos>");
            return false;
        }
        return true;
    }

    private boolean validarPin(String pin) throws IOException {
        if (!pin.matches("\\d{4}")) {
            cliente.enviarMensaje("Sistema: El PIN debe ser de 4 dígitos numéricos.");
            return false;
        }
        return true;
    }

    private boolean validarNombre(String nuevoNombre, String oldNombreCliente) throws IOException {

        if (nuevoNombre.startsWith("/")) {
            cliente.enviarMensaje("Sistema: El nombre de usuario no puede empezar con '/'.");
            return false;
        }

        if (nuevoNombre.toLowerCase().startsWith("anonimo") && !nuevoNombre.equals(oldNombreCliente)) {
            cliente.enviarMensaje("Sistema: El nombre de usuario '" + nuevoNombre + "' está reservado.");
            return false;
        }

        return true;
    }

    private void manejarRegistro(String nuevoNombre, String pin, String oldNombreCliente) throws IOException {
        if (RUsuarios.UsuarioExistente(nuevoNombre)) {
            cliente.enviarMensaje("Sistema: Error al registrar. El usuario '" + nuevoNombre + "' ya existe. Usa /login.");
        } else if (RUsuarios.RegistrarUsuario(nuevoNombre, pin)) {
            autenticacionExitosa(nuevoNombre, oldNombreCliente, nuevoNombre + " acaba de registrarse");
            cliente.enviarMensaje("Sistema:Registro completado Tu nombre ahora es '" + nuevoNombre + "'.");
        } else {
            cliente.enviarMensaje("Sistema: Error al registrar. Intenta de nuevo.");
        }
    }

    private void manejarInicioSesion(String nuevoNombre, String pin, String oldNombreCliente) throws IOException {
        if (!RUsuarios.UsuarioExistente(nuevoNombre)) {
            cliente.enviarMensaje("Sistema: Error al iniciar sesión. Usuario '" + nuevoNombre + "' no registrado. Usa /register.");
        } else if (RUsuarios.AutenticarUsuario(nuevoNombre, pin)) {
            autenticacionExitosa(nuevoNombre, oldNombreCliente, nuevoNombre + " ha iniciado sesion");
            cliente.enviarMensaje("Sistema:Inicio de sesión exitoso, tu nombre ahora es '" + nuevoNombre + "'.");
        } else {
            cliente.enviarMensaje("Sistema: PIN incorrecto para el usuario '" + nuevoNombre + "'.");
        }
    }

    private void autenticacionExitosa(String nuevoNombre, String oldNombreCliente, String notificacion) throws IOException {
        servidor.removerCliente(oldNombreCliente);
        cliente.setNombreCliente(nuevoNombre);
        servidor.agregarCliente(nuevoNombre, cliente);

        cliente.setEstadoAutenticado();

        Mensaje.notificarATodos(notificacion, cliente, servidor);

        cliente.setCurrentGroup(RGrupos.ID_TODOS, RGrupos.NOMBRE_TODOS);
        cliente.enviarMensaje("Sistema: Has iniciado sesión. Estás en el grupo '" + cliente.getCurrentGroupName() + "'.");
        cliente.enviarMensajesPendientes();
    }
}