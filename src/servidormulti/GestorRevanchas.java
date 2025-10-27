package servidormulti;

import juego.JuegoGato;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GestorRevanchas {
    private final Map<String, RevanchaEstado> estadosRevancha;
    private final ServidorMulti servidor;
    private final ControladorJuego controladorJuego;

    public GestorRevanchas(ServidorMulti servidor, ControladorJuego controladorJuego) {
        this.estadosRevancha = Collections.synchronizedMap(new HashMap<>());
        this.servidor = servidor;
        this.controladorJuego = controladorJuego;
    }

    private static class RevanchaEstado {
        final JuegoGato juegoFinalizado;
        Boolean jugador1Acepta = null;
        Boolean jugador2Acepta = null;

        RevanchaEstado(JuegoGato juego) {
            this.juegoFinalizado = juego;
        }
    }

    public boolean estaEnEsperaDeRevancha(String nombreCliente) {
        return estadosRevancha.containsKey(nombreCliente);
    }

    public void registrarRevancha(JuegoGato juego) {
        RevanchaEstado estado = new RevanchaEstado(juego);
        estadosRevancha.put(juego.getJugadorX().getNombreCliente(), estado);
        estadosRevancha.put(juego.getJugadorO().getNombreCliente(), estado);
    }

    public void manejarRespuesta(UnCliente cliente, String respuesta) throws IOException {
        String nombre = cliente.getNombreCliente();
        RevanchaEstado estado = estadosRevancha.get(nombre);

        if (estado == null) {
            cliente.enviarMensaje("Sistema Gato: No hay ninguna propuesta de revancha pendiente para responder.");
            return;
        }

        if (respuesta.equalsIgnoreCase("/si")) {
            registrarVoto(cliente, estado, true);
        } else if (respuesta.equalsIgnoreCase("/no")) {
            registrarVoto(cliente, estado, false);
        } else {
            cliente.enviarMensaje("Sistema Gato: Respuesta inválida. Usa /si o /no.");
        }
    }

    private void registrarVoto(UnCliente cliente, RevanchaEstado estado, boolean acepta) throws IOException {
        UnCliente otroCliente = estado.juegoFinalizado.getContrincante(cliente);
        String otroNombre = otroCliente.getNombreCliente();

        actualizarEstadoVoto(cliente, estado, acepta);

        if (!acepta) {
            finalizarRevancha(cliente, otroCliente, otroNombre, true);
            return;
        }

        if (estado.jugador1Acepta != null && estado.jugador2Acepta != null) {
            iniciarNuevaPartida(estado.juegoFinalizado);
        } else {
            notificarEspera(cliente, otroCliente, otroNombre);
        }
    }

    private void actualizarEstadoVoto(UnCliente cliente, RevanchaEstado estado, boolean acepta) {
        if (cliente == estado.juegoFinalizado.getJugadorX()) {
            estado.jugador1Acepta = acepta;
        } else {
            estado.jugador2Acepta = acepta;
        }
    }

    private void finalizarRevancha(UnCliente cliente, UnCliente oponente, String otroNombre, boolean notificar) throws IOException {
        if(notificar) notificarRechazo(cliente, oponente);
        controladorJuego.removerJuego(cliente.getNombreCliente(), otroNombre);
        estadosRevancha.remove(cliente.getNombreCliente());
        estadosRevancha.remove(otroNombre);
    }

    private void notificarRechazo(UnCliente cliente, UnCliente oponente) throws IOException {
        cliente.enviarMensaje("Sistema Gato: Revancha rechazada. La partida ha terminado.");
        oponente.enviarMensaje("Sistema Gato: " + cliente.getNombreCliente() + " ha rechazado la revancha. La partida ha terminado.");
    }

    private void notificarEspera(UnCliente cliente, UnCliente oponente, String otroNombre) throws IOException {
        cliente.enviarMensaje("Sistema Gato: Voto registrado. Esperando respuesta de " + otroNombre + ".");
        oponente.enviarMensaje("Sistema Gato: " + cliente.getNombreCliente() + " ha aceptado. Responde /si o /no.");
    }

    private void iniciarNuevaPartida(JuegoGato juegoAnterior) throws IOException {
        String n1 = juegoAnterior.getJugadorX().getNombreCliente();
        String n2 = juegoAnterior.getJugadorO().getNombreCliente();

        controladorJuego.removerJuego(n1, n2);
        estadosRevancha.remove(n1);
        estadosRevancha.remove(n2);

        controladorJuego.iniciarJuego(juegoAnterior.getJugadorX(), juegoAnterior.getJugadorO());
    }

    public void cancelarPropuestasPendientes(String nombreDesconectado) throws IOException {
        RevanchaEstado estado = estadosRevancha.get(nombreDesconectado);

        if (estado != null) {
            UnCliente oponente = estado.juegoFinalizado.getContrincante(servidor.getCliente(nombreDesconectado));

            if (oponente != null) {
                oponente.enviarMensaje("Sistema Gato: Revancha cancelada. El oponente se desconectó.");
                finalizarRevancha(oponente, servidor.getCliente(nombreDesconectado), nombreDesconectado, false);
            } else {
                estadosRevancha.remove(nombreDesconectado);
            }
        }
    }
}