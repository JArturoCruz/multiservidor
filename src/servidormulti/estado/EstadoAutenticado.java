package servidormulti.estado;

import mensaje.Mensaje;
import servidormulti.*;
import java.io.IOException;
import java.util.Set;

public class EstadoAutenticado implements EstadoCliente {

    private final UnCliente cliente;
    private final ServidorMulti servidor;
    private final ManejadorComandosJuego manejadorJuego;
    private final ManejadorGrupos manejadorGrupos;
    private final ManejadorEstadisticas manejadorEstadisticas;
    private final ControladorBloqueo controladorBloqueo;
    private final ControladorJuego controladorJuego;

    public EstadoAutenticado(UnCliente cliente, ServidorMulti servidor) {
        this.cliente = cliente;
        this.servidor = servidor;
        this.controladorJuego = servidor.getControladorJuego();
        this.manejadorJuego = new ManejadorComandosJuego(controladorJuego, servidor.getGestorPropuestas());
        this.manejadorGrupos = new ManejadorGrupos(cliente, servidor);
        this.manejadorEstadisticas = new ManejadorEstadisticas(cliente);
        this.controladorBloqueo = new ControladorBloqueo(cliente);
    }

    @Override
    public void procesarMensaje(String mensaje) throws IOException {
        String comando = mensaje.split(" ", 2)[0].toLowerCase();

        if (comando.equals("/logout")) {
            cliente.setEstadoInvitado();
            return;
        }

        if (estaEnInteraccionJuego()) {
            manejarMensajeEnJuego(mensaje, comando);
            return;
        }

        if (esComandoJuego(comando)) {
            if (validarGrupoParaJuego(mensaje, comando)) {
                manejadorJuego.manejarComando(mensaje, cliente);
            }
        } else if (esComandoGrupo(comando)) {
            manejadorGrupos.manejar(mensaje, comando);
        } else if (esComandoEstadisticas(comando)) {
            manejadorEstadisticas.manejar(mensaje, comando);
        } else if (esComandoBloqueo(comando)) {
            controladorBloqueo.manejarComando(mensaje);
        } else if (comando.startsWith("/")) {
            cliente.enviarMensaje("Sistema: Comando no reconocido.");
        } else {
            Mensaje.procesar(mensaje, cliente, servidor);
        }
    }

    private void manejarMensajeEnJuego(String mensaje, String comando) throws IOException {
        if (esComandoJuego(comando)) {
            manejadorJuego.manejarComando(mensaje, cliente);
        } else if (esComandoEstadisticas(comando)) {
            manejadorEstadisticas.manejar(mensaje, comando);
        } else if (mensaje.startsWith("@")) {
            manejarChatPrivadoJuego(mensaje);
        } else {
            cliente.enviarMensaje("Sistema: Chat público bloqueado. Usa @<oponente> <mensaje> o comandos de juego.");
        }
    }

    private void manejarChatPrivadoJuego(String mensaje) throws IOException {
        String[] partes = mensaje.split(" ", 2);
        String nombreDestino = partes[0].substring(1);
        Set<String> oponentes = controladorJuego.getOponentesActivos(cliente.getNombreCliente());

        if (oponentes.contains(nombreDestino)) {
            Mensaje.enviarMensajePrivadoEntreJugadores(mensaje, cliente, nombreDestino, servidor);
        } else {
            cliente.enviarMensaje("Sistema: Solo puedes enviar mensajes privados a tus oponentes activos.");
        }
    }

    private boolean validarGrupoParaJuego(String mensaje, String comando) throws IOException {
        if (!comando.equals("/gato") && !comando.equals("/accept")) return true;

        String oponenteNombre = obtenerOponenteDeComando(mensaje);
        if (oponenteNombre == null) {
            cliente.enviarMensaje("Sistema: Formato incorrecto. Uso: " + comando + " <usuario>");
            return false;
        }
        UnCliente oponente = servidor.getCliente(oponenteNombre);
        if (oponente == null) {
            cliente.enviarMensaje("Sistema: El usuario '" + oponenteNombre + "' no está conectado.");
            return false;
        }
        if (cliente.getCurrentGroupId() != oponente.getCurrentGroupId()) {
            cliente.enviarMensaje("Sistema: Solo puedes jugar con usuarios en tu mismo grupo (" + cliente.getCurrentGroupName() + ").");
            cliente.enviarMensaje("Sistema: '" + oponenteNombre + "' está en el grupo '" + oponente.getCurrentGroupName() + "'.");
            return false;
        }
        return true;
    }

    private boolean estaEnInteraccionJuego() {
        return controladorJuego.estaJugando(cliente.getNombreCliente()) || controladorJuego.tieneRevanchaPendiente(cliente.getNombreCliente());
    }
    private String obtenerOponenteDeComando(String mensaje) {
        String[] partes = mensaje.split(" ", 3);
        return (partes.length >= 2) ? partes[1].trim() : null;
    }
    private boolean esComandoJuego(String c) { return c.equals("/move") || c.equals("/gato") || c.equals("/accept") || c.equals("/reject") || c.equals("/si") || c.equals("/no"); }
    private boolean esComandoEstadisticas(String c) { return c.equals("/ranking") || c.equals("/vs"); }
    private boolean esComandoGrupo(String c) { return c.equals("/gcreate") || c.equals("/gdelete") || c.equals("/join") || c.equals("/glist"); }
    private boolean esComandoBloqueo(String c) { return c.equals("/block") || c.equals("/unblock"); }
}