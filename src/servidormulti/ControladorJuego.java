package servidormulti;

import juego.JuegoGato;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import bd.BDusuarios;

public class ControladorJuego {
    private final Map<String, Map<String, JuegoGato>> juegosActivos;
    private final Map<String, Map<String, JuegoGato>> juegosRevanchaPendiente;
    private final Map<String, Set<String>> revanchaAceptada;
    private final ServidorMulti servidor;
    private final GestorPropuestas gestorPropuestas;

    public ControladorJuego(ServidorMulti servidor, GestorPropuestas gestorPropuestas) {
        this.juegosActivos = Collections.synchronizedMap(new HashMap<>());
        this.juegosRevanchaPendiente = Collections.synchronizedMap(new HashMap<>());
        this.revanchaAceptada = Collections.synchronizedMap(new HashMap<>());
        this.servidor = servidor;
        this.gestorPropuestas = gestorPropuestas;
    }

    public boolean estaJugando(String nombre) {
        return juegosActivos.containsKey(nombre) && !juegosActivos.get(nombre).isEmpty();
    }

    public boolean tieneRevanchaPendiente(String nombre) {
        return juegosRevanchaPendiente.containsKey(nombre) && !juegosRevanchaPendiente.get(nombre).isEmpty();
    }

    public boolean estaJugandoCon(String clienteNombre, String oponenteNombre) {
        Map<String, JuegoGato> juegosDelCliente = juegosActivos.get(clienteNombre);
        Map<String, JuegoGato> revanchasDelCliente = juegosRevanchaPendiente.get(clienteNombre);

        boolean activo = juegosDelCliente != null && juegosDelCliente.containsKey(oponenteNombre);
        boolean revancha = revanchasDelCliente != null && revanchasDelCliente.containsKey(oponenteNombre);

        return activo || revancha;
    }

    public JuegoGato getJuegoActivo(String clienteNombre, String oponenteNombre) {
        Map<String, JuegoGato> juegosDelCliente = juegosActivos.get(clienteNombre);
        if (juegosDelCliente == null) return null;
        return juegosDelCliente.get(oponenteNombre);
    }

    public boolean tieneInvitacionPendiente(String nombreCliente) {
        return gestorPropuestas.tieneInvitacionPendiente(nombreCliente);
    }

    public Set<String> getOponentesActivos(String clienteNombre) {
        Set<String> oponentes = new HashSet<>();

        Map<String, JuegoGato> juegosDelCliente = juegosActivos.get(clienteNombre);
        if (juegosDelCliente != null) oponentes.addAll(juegosDelCliente.keySet());

        Map<String, JuegoGato> revanchasDelCliente = juegosRevanchaPendiente.get(clienteNombre);
        if (revanchasDelCliente != null) oponentes.addAll(revanchasDelCliente.keySet());

        return oponentes;
    }

    public void iniciarJuego(UnCliente proponente, UnCliente aceptante) throws IOException {
        JuegoGato juego = new JuegoGato(proponente, aceptante);

        String nombreProp = proponente.getNombreCliente();
        String nombreAcep = aceptante.getNombreCliente();

        juegosActivos.computeIfAbsent(nombreProp, k -> Collections.synchronizedMap(new HashMap<>())).put(nombreAcep, juego);
        juegosActivos.computeIfAbsent(nombreAcep, k -> Collections.synchronizedMap(new HashMap<>())).put(nombreProp, juego);

        proponente.enviarMensaje("Sistema Gato: ¡Juego iniciado con " + nombreAcep + "! Empiezas tú. Usa /move " + nombreAcep + " <fila> <columna>.");
        aceptante.enviarMensaje("Sistema Gato: ¡Juego iniciado con " + nombreProp + "! Espera el movimiento de " + nombreProp + ".");
    }

    public void manejarMovimiento(UnCliente cliente, String nombreOponente, String sFila, String sColumna) throws IOException {
        String clienteNombre = cliente.getNombreCliente();

        JuegoGato juego = getJuegoActivo(clienteNombre, nombreOponente);

        if (juego == null) {
            cliente.enviarMensaje("Sistema Gato: No estás en un juego activo contra '" + nombreOponente + "'. Usa /gato <usuario> para proponer uno.");
            return;
        }

        if (juego.getEstado() != JuegoGato.EstadoJuego.ACTIVO) {
            cliente.enviarMensaje("Sistema Gato: El juego con '" + nombreOponente + "' ha terminado (" + juego.getEstado().name() + "). Usa /si <oponente> o /no <oponente> para responder la revancha.");
            return;
        }

        try {
            int fila = Integer.parseInt(sFila);
            int columna = Integer.parseInt(sColumna);
            boolean juegoTerminado = juego.realizarMovimiento(cliente, fila, columna);

            if (juegoTerminado) {
                String ganador = juego.getGanador() != null ? juego.getGanador().getNombreCliente() : null;
                String jugador1 = juego.getJugadorX().getNombreCliente();
                String jugador2 = juego.getJugadorO().getNombreCliente();

                BDusuarios.registrarResultadoPartida(jugador1, jugador2, ganador);

                solicitarRevancha(cliente, juego, nombreOponente);
            }
        } catch (NumberFormatException e) {
            cliente.enviarMensaje("Sistema Gato: Formato incorrecto. Usa /move <oponente> <fila> <columna> (ej: /move " + nombreOponente + " 1 3)");
        }
    }

    private void solicitarRevancha(UnCliente clienteQueMovio, JuegoGato juego, String nombreOponente) throws IOException {
        String nombreCliente = clienteQueMovio.getNombreCliente();
        UnCliente oponente = servidor.getCliente(nombreOponente);

        removerJuegoDeActivos(nombreCliente, nombreOponente);

        juegosRevanchaPendiente.computeIfAbsent(nombreCliente, k -> Collections.synchronizedMap(new HashMap<>())).put(nombreOponente, juego);
        juegosRevanchaPendiente.computeIfAbsent(nombreOponente, k -> Collections.synchronizedMap(new HashMap<>())).put(nombreCliente, juego);

        UnCliente ganador = juego.getGanador();

        if (ganador != null) {
            UnCliente perdedor = (ganador == clienteQueMovio) ? oponente : clienteQueMovio;

            ganador.enviarMensaje("Sistema Gato: ¡Ganaste contra " + perdedor.getNombreCliente() + "! ¿Quieres la revancha? Usa /si " + perdedor.getNombreCliente() + " o /no " + perdedor.getNombreCliente() + ".");
            if (perdedor != null) {
                perdedor.enviarMensaje("Sistema Gato: Perdiste contra " + ganador.getNombreCliente() + ". ¿Quieres la revancha? Responde con /si " + ganador.getNombreCliente() + " o /no " + ganador.getNombreCliente() + ".");
            }
        } else {
            clienteQueMovio.enviarMensaje("Sistema Gato: El juego terminó en empate contra " + nombreOponente + ". ¿Revancha? Usa /si " + nombreOponente + " o /no " + nombreOponente + ".");
            if (oponente != null) {
                oponente.enviarMensaje("Sistema Gato: El juego terminó en empate contra " + nombreCliente + ". ¿Revancha? Responde con /si " + nombreCliente + " o /no " + nombreCliente + ".");
            }
        }
    }

    public void manejarRespuestaRevancha(UnCliente cliente, String nombreOponente, boolean acepta) throws IOException {
        String nombreCliente = cliente.getNombreCliente();

        JuegoGato juegoAnterior = juegosRevanchaPendiente.getOrDefault(nombreCliente, Collections.emptyMap()).get(nombreOponente);

        if (juegoAnterior == null) {
            cliente.enviarMensaje("Sistema Gato: No tienes un juego terminado pendiente de revancha con " + nombreOponente + ".");
            return;
        }

        UnCliente oponente = servidor.getCliente(nombreOponente);
        if (oponente == null || !servidor.clienteEstaConectado(nombreOponente)) {
            cliente.enviarMensaje("Sistema Gato: " + nombreOponente + " se desconectó. Revancha cancelada.");
            removerJuegoDeRevanchaPendiente(nombreCliente, nombreOponente);
            removerAceptacion(nombreCliente, nombreOponente);
            removerAceptacion(nombreOponente, nombreCliente);
            return;
        }

        if (acepta) {

            revanchaAceptada.computeIfAbsent(nombreCliente, k -> Collections.synchronizedSet(new HashSet<>())).add(nombreOponente);

            boolean oponenteYaAcepto = revanchaAceptada.getOrDefault(nombreOponente, Collections.emptySet()).contains(nombreCliente);

            if (oponenteYaAcepto) {
                iniciarJuego(cliente, oponente);

                removerJuegoDeRevanchaPendiente(nombreCliente, nombreOponente);
                removerAceptacion(nombreCliente, nombreOponente);
                removerAceptacion(nombreOponente, nombreCliente);

            } else {
                cliente.enviarMensaje("Sistema Gato: Has aceptado la revancha contra " + nombreOponente + ". Esperando la respuesta de " + nombreOponente + ".");
                oponente.enviarMensaje("Sistema Gato: " + nombreCliente + " ha aceptado la revancha. Responde con /si " + nombreCliente + " o /no " + nombreCliente + " para empezar.");
            }

        } else {

            cliente.enviarMensaje("Sistema Gato: Revancha rechazada. Partida finalizada contra " + nombreOponente + ".");
            oponente.enviarMensaje("Sistema Gato: " + nombreCliente + " rechazó la revancha. Partida finalizada.");

            removerJuegoDeRevanchaPendiente(nombreCliente, nombreOponente);
            removerAceptacion(nombreCliente, nombreOponente);
            removerAceptacion(nombreOponente, nombreCliente);
        }
    }

    public void removerJuego(String nombre1, String nombre2) {
        removerJuegoDeActivos(nombre1, nombre2);
        removerJuegoDeRevanchaPendiente(nombre1, nombre2);
    }

    private void removerAceptacion(String nombre1, String nombre2) {
        Set<String> aceptaciones1 = revanchaAceptada.get(nombre1);
        if (aceptaciones1 != null && nombre2 != null) {
            aceptaciones1.remove(nombre2);
            if (aceptaciones1.isEmpty()) {
                revanchaAceptada.remove(nombre1);
            }
        } else if (nombre2 == null && aceptaciones1 != null) {
            revanchaAceptada.remove(nombre1);
        }
    }

    private void removerJuegoDeActivos(String nombre1, String nombre2) {
        Map<String, JuegoGato> juegos1 = juegosActivos.get(nombre1);
        if (juegos1 != null) {
            juegos1.remove(nombre2);
            if (juegos1.isEmpty()) {
                juegosActivos.remove(nombre1);
            }
        }

        Map<String, JuegoGato> juegos2 = juegosActivos.get(nombre2);
        if (juegos2 != null) {
            juegos2.remove(nombre1);
            if (juegos2.isEmpty()) {
                juegosActivos.remove(nombre2);
            }
        }
    }

    private JuegoGato removerJuegoDeRevanchaPendiente(String nombre1, String nombre2) {
        JuegoGato juego = null;

        Map<String, JuegoGato> juegos1 = juegosRevanchaPendiente.get(nombre1);
        if (juegos1 != null) {
            juego = juegos1.remove(nombre2);
            if (juegos1.isEmpty()) {
                juegosRevanchaPendiente.remove(nombre1);
            }
        }

        Map<String, JuegoGato> juegos2 = juegosRevanchaPendiente.get(nombre2);
        if (juegos2 != null) {
            juegos2.remove(nombre1);
            if (juegos2.isEmpty()) {
                juegosRevanchaPendiente.remove(nombre2);
            }
        }
        return juego;
    }

    public void finalizarPorDesconexion(UnCliente desconectado) throws IOException {
        String nombreDesconectado = desconectado.getNombreCliente();

        manejarJuegosActivosPorAbandono(desconectado);
        manejarJuegosRevanchaPorAbandono(desconectado);
        gestorPropuestas.cancelarPropuestasPendientes(nombreDesconectado);
    }

    private void manejarJuegosActivosPorAbandono(UnCliente desconectado) throws IOException {
        String nombreDesconectado = desconectado.getNombreCliente();
        Map<String, JuegoGato> juegos = juegosActivos.get(nombreDesconectado);

        if (juegos != null) {
            Set<String> oponentes = new HashSet<>(juegos.keySet());

            for (String nombreOponente : oponentes) {
                JuegoGato juego = juegos.get(nombreOponente);
                UnCliente oponente = servidor.getCliente(nombreOponente);

                juego.finalizarPorAbandono(desconectado);

                String ganador = null;
                if(oponente != null) {
                    ganador = oponente.getNombreCliente();
                    oponente.enviarMensaje("Sistema Gato: La partida contra " + nombreDesconectado + " ha finalizado por desconexión. ¡Has ganado por abandono!");

                    String jugador1 = juego.getJugadorX().getNombreCliente();
                    String jugador2 = juego.getJugadorO().getNombreCliente();

                    BDusuarios.registrarResultadoPartida(jugador1, jugador2, ganador);
                } else {

                }

                removerJuegoDeActivos(nombreDesconectado, nombreOponente);
            }
        }
    }

    private void manejarJuegosRevanchaPorAbandono(UnCliente desconectado) throws IOException {
        String nombreDesconectado = desconectado.getNombreCliente();
        Map<String, JuegoGato> juegos = juegosRevanchaPendiente.get(nombreDesconectado);

        if (juegos != null) {
            Set<String> oponentes = new HashSet<>(juegos.keySet());

            for (String nombreOponente : oponentes) {
                UnCliente oponente = servidor.getCliente(nombreOponente);

                if(oponente != null) {
                    oponente.enviarMensaje("Sistema Gato: La solicitud de revancha contra " + nombreDesconectado + " ha sido cancelada por desconexión.");
                }
                removerJuegoDeRevanchaPendiente(nombreDesconectado, nombreOponente);
                removerAceptacion(nombreDesconectado, nombreOponente);
                removerAceptacion(nombreOponente, nombreDesconectado);
            }
        }
        removerAceptacion(nombreDesconectado, null);
    }
}