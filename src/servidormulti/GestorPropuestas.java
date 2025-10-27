package servidormulti;

import bd.BDusuarios;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GestorPropuestas {
    private final Map<String, String> propuestasPendientes;
    private final ServidorMulti servidor;
    private final ControladorJuego controladorJuego;

    public GestorPropuestas(ServidorMulti servidor, ControladorJuego controladorJuego) {
        this.propuestasPendientes = Collections.synchronizedMap(new HashMap<>());
        this.servidor = servidor;
        this.controladorJuego = controladorJuego;
    }
    public boolean tieneInvitacionPendiente(String nombreCliente) {
        return propuestasPendientes.containsValue(nombreCliente);
    }

    private boolean tienePropuestaPendiente(String nombre1, String nombre2) {
        if (propuestasPendientes.getOrDefault(nombre1, "").equals(nombre2)) return true;
        if (propuestasPendientes.getOrDefault(nombre2, "").equals(nombre1)) return true;
        return false;
    }
    public void proponerJuego(UnCliente proponente, String nombreDestino) throws IOException {
        if (!validarEntradaPropuesta(proponente, nombreDestino)) return;
        String proponenteNombre = proponente.getNombreCliente();
        UnCliente clienteDestino = servidor.getCliente(nombreDestino);
        if (!validarCondicionesJuego(proponente, nombreDestino, clienteDestino)) return;
        propuestasPendientes.put(proponenteNombre, nombreDestino);
        notificarPropuesta(proponente, nombreDestino, clienteDestino);
    }

    private boolean validarEntradaPropuesta(UnCliente proponente, String nombreDestino) throws IOException {
        if (nombreDestino.isEmpty()) {
            proponente.enviarMensaje("Sistema Gato: Uso incorrecto. Usa /gato <usuario>");
            return false;
        }
        if (proponente.getNombreCliente().equals(nombreDestino)) {
            proponente.enviarMensaje("Sistema Gato: No puedes jugar al Gato contigo mismo.");
            return false;
        }
        return true;
    }

    private boolean validarCondicionesJuego(UnCliente proponente, String nombreDestino, UnCliente clienteDestino) throws IOException {
        if (!validarDestino(proponente, nombreDestino, clienteDestino)) return false;
        if (!validarBloqueo(proponente, nombreDestino)) return false;
        if (!validarEstadoActivo(proponente, nombreDestino)) return false;
        if (!validarPropuestasCruzadas(proponente, nombreDestino)) return false;
        return true;
    }

    private boolean validarDestino(UnCliente proponente, String nombreDestino, UnCliente clienteDestino) throws IOException {
        if (clienteDestino == null || !clienteDestino.isAutenticado()) {
            proponente.enviarMensaje("Sistema Gato: El usuario '" + nombreDestino + "' no está conectado o no está autenticado.");
            return false;
        }
        return true;
    }

    private boolean validarBloqueo(UnCliente proponente, String nombreDestino) throws IOException {
        String p = proponente.getNombreCliente();
        boolean bloqueadoPorDestino = BDusuarios.estaBloqueado(nombreDestino, p);
        boolean bloqueadoPorRemitente = BDusuarios.estaBloqueado(p, nombreDestino);

        if (bloqueadoPorDestino || bloqueadoPorRemitente) {
            String razon = bloqueadoPorDestino ? "El usuario te tiene bloqueado." : "Tienes bloqueado al usuario.";
            proponente.enviarMensaje("Sistema Gato: Error al proponer juego a '" + nombreDestino + "'. " + razon);
            return false;
        }
        return true;
    }

    private boolean validarEstadoActivo(UnCliente proponente, String nombreDestino) throws IOException {
        if (controladorJuego.estaJugando(proponente.getNombreCliente()) || controladorJuego.estaJugando(nombreDestino)) {
            proponente.enviarMensaje("Sistema Gato: Tú o el usuario '" + nombreDestino + "' ya están en una partida activa.");
            return false;
        }
        return true;
    }

    private boolean validarPropuestasCruzadas(UnCliente proponente, String nombreDestino) throws IOException {
        String p = proponente.getNombreCliente();
        if (tienePropuestaPendiente(p, nombreDestino) || tieneInvitacionPendiente(p) || tieneInvitacionPendiente(nombreDestino)) {
            proponente.enviarMensaje("Sistema Gato: Ya tienes una propuesta pendiente (enviada o recibida) con " + nombreDestino + ".");
            return false;
        }
        return true;
    }

    private void notificarPropuesta(UnCliente proponente, String nombreDestino, UnCliente clienteDestino) throws IOException {
        proponente.enviarMensaje("Sistema Gato: Propuesta enviada a " + nombreDestino + ". Esperando respuesta...");
        clienteDestino.enviarMensaje("Sistema Gato: ¡" + proponente.getNombreCliente() + " te ha propuesto jugar al Gato! Usa /accept " + proponente.getNombreCliente() + " o /reject " + proponente.getNombreCliente() + ".");
    }

    public void aceptarPropuesta(UnCliente aceptante, String nombreProponente) throws IOException {
        if (nombreProponente.isEmpty()) {
            aceptante.enviarMensaje("Sistema Gato: Uso incorrecto. Usa /accept <usuario>");
            return;
        }

        String proponenteEsperado = propuestasPendientes.get(nombreProponente);
        if (proponenteEsperado == null || !proponenteEsperado.equals(aceptante.getNombreCliente())) {
            aceptante.enviarMensaje("Sistema Gato: El usuario '" + nombreProponente + "' no te ha propuesto un juego.");
            return;
        }

        UnCliente proponente = servidor.getCliente(nombreProponente);
        if (!validarProponente(aceptante, nombreProponente, proponente)) return;

        if (controladorJuego.estaJugando(aceptante.getNombreCliente()) || controladorJuego.estaJugando(nombreProponente)) {
            aceptante.enviarMensaje("Sistema Gato: Tú o el proponente están ahora en una partida activa.");
            propuestasPendientes.remove(nombreProponente);
            return;
        }

        propuestasPendientes.remove(nombreProponente);
        controladorJuego.iniciarJuego(proponente, aceptante);
    }

    private boolean validarProponente(UnCliente aceptante, String nombreProponente, UnCliente proponente) throws IOException {
        if (proponente == null) {
            aceptante.enviarMensaje("Sistema Gato: El proponente se ha desconectado. No se pudo iniciar el juego.");
            propuestasPendientes.remove(nombreProponente);
            return false;
        }
        return true;
    }

    public void rechazarPropuesta(UnCliente rechazante, String nombreProponente) throws IOException {
        if (nombreProponente.isEmpty()) {
            rechazante.enviarMensaje("Sistema Gato: Uso incorrecto. Usa /reject <usuario>");
            return;
        }

        String proponenteEsperado = propuestasPendientes.get(nombreProponente);
        if (proponenteEsperado == null || !proponenteEsperado.equals(rechazante.getNombreCliente())) {
            rechazante.enviarMensaje("Sistema Gato: El usuario '" + nombreProponente + "' no te ha propuesto un juego.");
            return;
        }
        propuestasPendientes.remove(nombreProponente);
        notificarRechazo(rechazante, nombreProponente);
    }

    private void notificarRechazo(UnCliente rechazante, String nombreProponente) throws IOException {
        UnCliente proponente = servidor.getCliente(nombreProponente);

        if (proponente != null) {
            proponente.enviarMensaje("Sistema Gato: " + rechazante.getNombreCliente() + " ha rechazado tu propuesta de juego.");
        }
        rechazante.enviarMensaje("Sistema Gato: Has rechazado la propuesta de " + nombreProponente + ".");
    }

    public void cancelarPropuestasPendientes(String nombreDesconectado) throws IOException {
        cancelarPropuestasSalientes(nombreDesconectado);
        cancelarPropuestasEntrantes(nombreDesconectado);
    }

    private void cancelarPropuestasSalientes(String nombreDesconectado) throws IOException {
        String nombreDestino = propuestasPendientes.remove(nombreDesconectado);
        if (nombreDestino != null) {
            UnCliente destino = servidor.getCliente(nombreDestino);
            if (destino != null) {
                destino.enviarMensaje("Sistema Gato: La propuesta de juego de " + nombreDesconectado + " ha sido cancelada.");
            }
        }
    }

    private void cancelarPropuestasEntrantes(String nombreDesconectado) throws IOException {
        for(Map.Entry<String, String> entry : propuestasPendientes.entrySet()) {
            if (entry.getValue().equals(nombreDesconectado)) {
                String nombreProponente = entry.getKey();
                propuestasPendientes.remove(nombreProponente);

                UnCliente proponente = servidor.getCliente(nombreProponente);
                if (proponente != null) {
                    proponente.enviarMensaje("Sistema Gato: La propuesta de juego a " + nombreDesconectado + " ha sido cancelada.");
                }
                break;
            }
        }
    }
}