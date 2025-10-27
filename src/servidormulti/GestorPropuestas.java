package servidormulti;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class GestorPropuestas {

    private final Map<String, Map<String, Propuesta>> propuestasPendientes;
    private final ServidorMulti servidor;
    private final ControladorJuego controladorJuego;

    public GestorPropuestas(ServidorMulti servidor, ControladorJuego controladorJuego) {
        this.propuestasPendientes = Collections.synchronizedMap(new HashMap<>());
        this.servidor = servidor;
        this.controladorJuego = controladorJuego;
    }

    public boolean tieneInvitacionPendiente(String nombreCliente) {
        Map<String, Propuesta> invitaciones = propuestasPendientes.get(nombreCliente);
        return invitaciones != null && !invitaciones.isEmpty();
    }

    public void proponerJuego(UnCliente proponente, String nombreInvitado) throws IOException {
        String nombreProponente = proponente.getNombreCliente();

        if (nombreProponente.equalsIgnoreCase(nombreInvitado)) {
            proponente.enviarMensaje("Sistema Gato: No puedes invitarte a ti mismo.");
            return;
        }

        if (controladorJuego.estaJugandoCon(nombreProponente, nombreInvitado)) {
            proponente.enviarMensaje("Sistema Gato: Ya tienes una partida activa con " + nombreInvitado + ".");
            return;
        }

        UnCliente invitado = servidor.getCliente(nombreInvitado);
        if (invitado == null || !invitado.isAutenticado()) {
            proponente.enviarMensaje("Sistema Gato: El usuario '" + nombreInvitado + "' no está conectado o no está autenticado.");
            return;
        }

        if (controladorJuego.estaJugandoCon(nombreInvitado, nombreProponente)) {
            proponente.enviarMensaje("Sistema Gato: " + nombreInvitado + " ya tiene una partida activa contigo. No puedes iniciar otra.");
            return;
        }

        Map<String, Propuesta> invitacionesDelInvitado = propuestasPendientes.get(nombreInvitado);
        if (invitacionesDelInvitado != null && invitacionesDelInvitado.containsKey(nombreProponente)) {
            proponente.enviarMensaje("Sistema Gato: Ya enviaste una invitación a " + nombreInvitado + ". Espera su respuesta.");
            return;
        }

        Propuesta propuesta = new Propuesta(proponente, invitado);

        propuestasPendientes.computeIfAbsent(nombreInvitado, k -> Collections.synchronizedMap(new HashMap<>()))
                .put(nombreProponente, propuesta);

        proponente.enviarMensaje("Sistema Gato: Invitación enviada a " + nombreInvitado + ". Esperando respuesta.");
        invitado.enviarMensaje("Sistema Gato: Has recibido una invitación de " + nombreProponente + ". Responde con /accept " + nombreProponente + " o /reject " + nombreProponente + ".");
    }

    public void aceptarPropuesta(UnCliente aceptante, String nombreProponente) throws IOException {
        String nombreAceptante = aceptante.getNombreCliente();

        if (nombreProponente.isEmpty()) {
            aceptante.enviarMensaje("Sistema Gato: Comando incorrecto. Usa /accept <nombre_proponente>.");
            return;
        }

        Map<String, Propuesta> propuestasRecibidas = propuestasPendientes.get(nombreAceptante);
        if (propuestasRecibidas == null || !propuestasRecibidas.containsKey(nombreProponente)) {
            aceptante.enviarMensaje("Sistema Gato: No tienes una invitación pendiente de " + nombreProponente + ".");
            return;
        }

        Propuesta propuesta = propuestasRecibidas.get(nombreProponente);
        UnCliente proponente = propuesta.getProponente();

        if (!servidor.clienteEstaConectado(proponente.getNombreCliente())) {
            aceptante.enviarMensaje("Sistema Gato: El proponente (" + nombreProponente + ") se desconectó.");
            removerPropuesta(nombreAceptante, nombreProponente);
            return;
        }

        controladorJuego.iniciarJuego(proponente, aceptante);
        removerPropuesta(nombreAceptante, nombreProponente);

        proponente.enviarMensaje("Sistema Gato: ¡" + nombreAceptante + " aceptó tu invitación! Empiezas tú. Usa /move " + nombreAceptante + " <fila> <columna>.");
    }

    public void rechazarPropuesta(UnCliente rechazador, String nombreProponente) throws IOException {
        String nombreRechazador = rechazador.getNombreCliente();

        if (nombreProponente.isEmpty()) {
            rechazador.enviarMensaje("Sistema Gato: Comando incorrecto. Usa /reject <nombre_proponente>.");
            return;
        }

        Map<String, Propuesta> propuestasRecibidas = propuestasPendientes.get(nombreRechazador);
        if (propuestasRecibidas == null || !propuestasRecibidas.containsKey(nombreProponente)) {
            rechazador.enviarMensaje("Sistema Gato: No tienes una invitación pendiente de " + nombreProponente + ".");
            return;
        }

        Propuesta propuesta = propuestasRecibidas.get(nombreProponente);
        UnCliente proponente = propuesta.getProponente();

        removerPropuesta(nombreRechazador, nombreProponente);
        rechazador.enviarMensaje("Sistema Gato: Has rechazado la invitación de " + nombreProponente + ".");

        if (servidor.clienteEstaConectado(proponente.getNombreCliente())) {
            proponente.enviarMensaje("Sistema Gato: " + nombreRechazador + " rechazó tu invitación.");
        }
    }

    private void removerPropuesta(String nombreInvitado, String nombreProponente) {
        Map<String, Propuesta> propuestas = propuestasPendientes.get(nombreInvitado);
        if (propuestas != null) {
            propuestas.remove(nombreProponente);
            if (propuestas.isEmpty()) {
                propuestasPendientes.remove(nombreInvitado);
            }
        }
    }

    public void cancelarPropuestasPendientes(String nombreCliente) throws IOException {
        // 1. Eliminar propuestas donde el cliente es el INVITADO (Recibidas)
        Map<String, Propuesta> invitacionesRecibidas = propuestasPendientes.remove(nombreCliente);
        if (invitacionesRecibidas != null) {
            for (Propuesta p : invitacionesRecibidas.values()) {
                UnCliente proponente = p.getProponente();
                if (servidor.clienteEstaConectado(proponente.getNombreCliente())) {
                    proponente.enviarMensaje("Sistema Gato: La invitación que enviaste a " + nombreCliente + " ha sido cancelada por desconexión de " + nombreCliente + ".");
                }
            }
        }

        // 2. Eliminar propuestas donde el cliente es el PROPONENTE (Enviadas)
        Set<String> invitadosConPropuestas = new HashSet<>(propuestasPendientes.keySet());
        for (String invitadoNombre : invitadosConPropuestas) {
            Map<String, Propuesta> propuestasDelInvitado = propuestasPendientes.get(invitadoNombre);
            if (propuestasDelInvitado != null && propuestasDelInvitado.containsKey(nombreCliente)) {
                propuestasDelInvitado.remove(nombreCliente);

                UnCliente invitado = servidor.getCliente(invitadoNombre);
                if (invitado != null && servidor.clienteEstaConectado(invitadoNombre)) {
                    invitado.enviarMensaje("Sistema Gato: La invitación de " + nombreCliente + " ha sido cancelada porque el proponente se desconectó.");
                }

                if (propuestasDelInvitado.isEmpty()) {
                    propuestasPendientes.remove(invitadoNombre);
                }
            }
        }
    }
}