package servidormulti;

import juego.JuegoGato;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ControladorJuego {
    private final Map<String, JuegoGato> juegosActivos;
    private final ServidorMulti servidor;
    private final GestorPropuestas gestorPropuestas;
    public ControladorJuego(ServidorMulti servidor) {
        this.juegosActivos = Collections.synchronizedMap(new HashMap<>());
        this.servidor = servidor;
        this.gestorPropuestas = new GestorPropuestas(servidor, this);
    }

    public boolean estaJugando(String nombre) {
        return juegosActivos.containsKey(nombre);
    }

    public JuegoGato getJuegoActivo(String nombre) {
        return juegosActivos.get(nombre);
    }

    public String getOponenteSiEstaJugando(String nombreCliente) {
        JuegoGato juego = juegosActivos.get(nombreCliente);
        if (juego != null) {
            UnCliente cliente = servidor.getCliente(nombreCliente);
            UnCliente oponente = juego.getContrincante(cliente);
            if (oponente != null) return oponente.getNombreCliente();
        }
        return null;
    }

    public boolean tieneInvitacionPendiente(String nombreCliente) {
        return gestorPropuestas.tieneInvitacionPendiente(nombreCliente);
    }

    public void iniciarJuego(UnCliente proponente, UnCliente aceptante) throws IOException {
        JuegoGato juego = new JuegoGato(proponente, aceptante);
        juegosActivos.put(proponente.getNombreCliente(), juego);
        juegosActivos.put(aceptante.getNombreCliente(), juego);
    }

    public void manejarComando(String mensaje, UnCliente remitente) throws IOException {
        if (!remitente.isAutenticado()) {
            remitente.enviarMensaje("Sistema Gato: Debes estar autenticado para jugar al Gato.");
            return;
        }

        String[] partes = mensaje.split(" ", 3);
        String comando = partes[0];
        String argumento1 = partes.length > 1 ? partes[1] : "";
        String argumento2 = partes.length > 2 ? partes[2] : "";

        switch (comando) {
            case "/gato":
                gestorPropuestas.proponerJuego(remitente, argumento1);
                break;
            case "/accept":
                gestorPropuestas.aceptarPropuesta(remitente, argumento1);
                break;
            case "/reject":
                gestorPropuestas.rechazarPropuesta(remitente, argumento1);
                break;
            case "/move":
                manejarMovimiento(remitente, argumento1, argumento2);
                break;
            default:
                remitente.enviarMensaje("Sistema Gato: Comando de juego desconocido.");
                break;
        }
    }
    private void manejarMovimiento(UnCliente cliente, String sFila, String sColumna) throws IOException {
        String clienteNombre = cliente.getNombreCliente();
        JuegoGato juego = getJuegoActivo(clienteNombre);

        if (juego == null) {
            cliente.enviarMensaje("Sistema Gato: No estás en un juego activo. Usa /gato <usuario> para proponer uno.");
            return;
        }
        if (!validarEstadoMovimiento(cliente, juego, clienteNombre)) return;
        try {
            int fila = Integer.parseInt(sFila);
            int columna = Integer.parseInt(sColumna);
            boolean juegoTerminado = juego.realizarMovimiento(cliente, fila, columna);
            if (juegoTerminado) {
                removerJuego(clienteNombre, juego.getContrincante(cliente).getNombreCliente());
            }
        } catch (NumberFormatException e) {
            cliente.enviarMensaje("Sistema Gato: Los argumentos para /move deben ser números. Usa /move <fila> <columna> (ej: /move 1 3)");
        }
    }

    private boolean validarEstadoMovimiento(UnCliente cliente, JuegoGato juego, String clienteNombre) throws IOException {
        if (juego.getEstado() != JuegoGato.EstadoJuego.ACTIVO) {
            cliente.enviarMensaje("Sistema Gato: El juego ha terminado (" + juego.getEstado().name() + "). Usa /gato para empezar uno nuevo.");
            removerJuego(clienteNombre, juego.getContrincante(cliente).getNombreCliente());
            return false;
        }
        return true;
    }

    public void finalizarPorDesconexion(UnCliente desconectado) throws IOException {
        String nombreDesconectado = desconectado.getNombreCliente();

        manejarJuegoActivoPorAbandono(desconectado);
        gestorPropuestas.cancelarPropuestasPendientes(nombreDesconectado);
    }

    private void manejarJuegoActivoPorAbandono(UnCliente desconectado) throws IOException {
        String nombreDesconectado = desconectado.getNombreCliente();
        JuegoGato juego = juegosActivos.get(nombreDesconectado);

        if (juego != null) {
            UnCliente oponente = juego.getContrincante(desconectado);
            String nombreOponente = oponente.getNombreCliente();

            juego.finalizarPorAbandono(desconectado);
            removerJuego(nombreDesconectado, nombreOponente);
        }
    }

    private void removerJuego(String nombre1, String nombre2) {
        juegosActivos.remove(nombre1);
        juegosActivos.remove(nombre2);
    }
}