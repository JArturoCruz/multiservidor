package servidormulti;

import bd.RUsuarios;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GestorPropuestas {

    private final Map<String, Set<Propuesta>> propuestasPendientes;
    private final ServidorMulti servidor;
    private ControladorJuego controladorJuego;

    public GestorPropuestas(ServidorMulti servidor) {
        this.propuestasPendientes = Collections.synchronizedMap(new HashMap<>());
        this.servidor = servidor;
    }

    public void setControladorJuego(ControladorJuego controladorJuego) {
        this.controladorJuego = controladorJuego;
    }

    public boolean tieneInvitacionPendiente(String nombreCliente) {
        return propuestasPendientes.containsKey(nombreCliente) && !propuestasPendientes.get(nombreCliente).isEmpty();
    }

    public void proponerJuego(UnCliente proponente, String nombreOponente) throws IOException {
        String nombreProponente = proponente.getNombreCliente();
        UnCliente oponente = servidor.getCliente(nombreOponente);

        if (oponente == null) {
            proponente.enviarMensaje("Sistema Gato: El usuario '" + nombreOponente + "' no está conectado.");
            return;
        }

        if (nombreProponente.equals(nombreOponente)) {
            proponente.enviarMensaje("Sistema Gato: No puedes jugar contigo mismo.");
            return;
        }

        if (controladorJuego.estaJugandoCon(nombreProponente, nombreOponente)) {
            proponente.enviarMensaje("Sistema Gato: Ya estás en un juego o revancha pendiente con " + nombreOponente + ".");
            return;
        }

        if (controladorJuego.tieneInvitacionPendiente(nombreProponente)) {
            proponente.enviarMensaje("Sistema Gato: Ya tienes una propuesta pendiente. Responde a tus invitaciones actuales primero.");
            return;
        }
        if (controladorJuego.tieneInvitacionPendiente(nombreOponente)) {
            proponente.enviarMensaje("Sistema Gato: " + nombreOponente + " tiene otras invitaciones pendientes. Intenta más tarde.");
            return;
        }

        if (RUsuarios.estaBloqueado(nombreProponente, nombreOponente) ||
                RUsuarios.estaBloqueado(nombreOponente, nombreProponente)) {
            proponente.enviarMensaje("Sistema Gato: No puedes proponer juego a '" + nombreOponente + "' (Bloqueo activo).");
            return;
        }

        Propuesta prop = new Propuesta(nombreProponente, nombreOponente);

        propuestasPendientes.computeIfAbsent(nombreProponente, k -> Collections.synchronizedSet(new HashSet<>())).add(prop);
        propuestasPendientes.computeIfAbsent(nombreOponente, k -> Collections.synchronizedSet(new HashSet<>())).add(prop);

        proponente.enviarMensaje("Sistema Gato: Propuesta enviada a " + nombreOponente + ". Esperando respuesta.");
        oponente.enviarMensaje("Sistema Gato: " + nombreProponente + " te ha invitado a jugar. Usa /accept " + nombreProponente + " o /reject " + nombreProponente + ".");
    }

    public void aceptarPropuesta(UnCliente aceptante, String nombreProponente) throws IOException {
        String nombreAceptante = aceptante.getNombreCliente();
        Propuesta prop = encontrarPropuesta(nombreAceptante, nombreProponente);

        if (prop == null) {
            aceptante.enviarMensaje("Sistema Gato: No hay una propuesta pendiente de " + nombreProponente + ".");
            return;
        }

        UnCliente proponente = servidor.getCliente(nombreProponente);
        if (proponente == null) {
            aceptante.enviarMensaje("Sistema Gato: " + nombreProponente + " se ha desconectado. Propuesta cancelada.");
            removerPropuesta(prop);
            return;
        }

        removerPropuesta(prop);
        controladorJuego.iniciarJuego(proponente, aceptante);
    }

    public void rechazarPropuesta(UnCliente rechazador, String nombreProponente) throws IOException {
        String nombreRechazador = rechazador.getNombreCliente();
        Propuesta prop = encontrarPropuesta(nombreRechazador, nombreProponente);

        if (prop == null) {
            rechazador.enviarMensaje("Sistema Gato: No hay una propuesta pendiente de " + nombreProponente + ".");
            return;
        }

        removerPropuesta(prop);

        UnCliente proponente = servidor.getCliente(nombreProponente);
        if (proponente != null) {
            proponente.enviarMensaje("Sistema Gato: " + nombreRechazador + " ha rechazado tu propuesta.");
        }
        rechazador.enviarMensaje("Sistema Gato: Has rechazado la propuesta de " + nombreProponente + ".");
    }

    public void cancelarPropuestasPendientes(String nombreCliente) {
        Set<Propuesta> props = propuestasPendientes.get(nombreCliente);
        if (props != null) {
            for (Propuesta p : new HashSet<>(props)) {
                removerPropuesta(p);

                String nombreOponente = p.getOponente(nombreCliente);

                UnCliente oponente = servidor.getCliente(nombreOponente);
                if (oponente != null) {
                    try {
                        oponente.enviarMensaje("Sistema Gato: La propuesta con " + nombreCliente + " fue cancelada por desconexión.");
                    } catch (IOException e) { }
                }
            }
        }
        propuestasPendientes.remove(nombreCliente);
    }

    private Propuesta encontrarPropuesta(String nombreCliente, String nombreProponente) {
        Set<Propuesta> props = propuestasPendientes.get(nombreCliente);
        if (props == null) return null;

        for (Propuesta p : props) {
            if (p.getProponente().equals(nombreProponente) && p.getInvitado().equals(nombreCliente)) {
                return p;
            }
        }
        return null;
    }

    private void removerPropuesta(Propuesta prop) {
        Set<Propuesta> props1 = propuestasPendientes.get(prop.getProponente());
        if (props1 != null) props1.remove(prop);

        Set<Propuesta> props2 = propuestasPendientes.get(prop.getInvitado());
        if (props2 != null) props2.remove(prop);
    }
}