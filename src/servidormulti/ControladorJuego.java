package servidormulti;

import juego.JuegoGato;
import bd.BDusuarios;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ControladorJuego {
    private final Map<String, String> propuestasPendientes;
    private final Map<String, JuegoGato> juegosActivos;
    private final ServidorMulti servidor;

    public ControladorJuego(ServidorMulti servidor) {
        this.propuestasPendientes = Collections.synchronizedMap(new HashMap<>());
        this.juegosActivos = Collections.synchronizedMap(new HashMap<>());
        this.servidor = servidor;
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
            if (oponente != null) {
                return oponente.getNombreCliente();
            }
        }
        return null;
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
                proponerJuego(remitente, argumento1);
                break;
            case "/accept":
                aceptarPropuesta(remitente, argumento1);
                break;
            case "/reject":
                rechazarPropuesta(remitente, argumento1);
                break;
            case "/move":
                manejarMovimiento(remitente, argumento1, argumento2);
                break;
            default:
                remitente.enviarMensaje("Sistema Gato: Comando de juego desconocido.");
                break;
        }
    }

    private void proponerJuego(UnCliente proponente, String nombreDestino) throws IOException {
        String proponenteNombre = proponente.getNombreCliente();

        if (nombreDestino.isEmpty()) {
            proponente.enviarMensaje("Sistema Gato: Uso incorrecto. Usa /gato <usuario>");
            return;
        }

        if (proponenteNombre.equals(nombreDestino)) {
            proponente.enviarMensaje("Sistema Gato: No puedes jugar al Gato contigo mismo.");
            return;
        }

        UnCliente clienteDestino = servidor.getCliente(nombreDestino);
        if (clienteDestino == null || !clienteDestino.isAutenticado()) {
            proponente.enviarMensaje("Sistema Gato: El usuario '" + nombreDestino + "' no está conectado o no está autenticado.");
            return;
        }

        boolean bloqueadoPorDestino = BDusuarios.estaBloqueado(nombreDestino, proponenteNombre);
        boolean bloqueadoPorRemitente = BDusuarios.estaBloqueado(proponenteNombre, nombreDestino);

        if (bloqueadoPorDestino || bloqueadoPorRemitente) {
            String razon = bloqueadoPorDestino ?
                    "El usuario te tiene bloqueado." :
                    "Tienes bloqueado al usuario.";

            proponente.enviarMensaje("Sistema Gato: Error al proponer juego a '" + nombreDestino + "'. " + razon + " No puedes jugar con alguien con quien tienes un bloqueo activo.");
            return;
        }

        if (estaJugando(proponenteNombre) || estaJugando(nombreDestino)) {
            proponente.enviarMensaje("Sistema Gato: Tú o el usuario '" + nombreDestino + "' ya están en una partida activa.");
            return;
        }

        if (tienePropuestaPendiente(proponenteNombre, nombreDestino)) {
            proponente.enviarMensaje("Sistema Gato: Ya tienes una propuesta pendiente (enviada o recibida) con " + nombreDestino + ".");
            return;
        }

        propuestasPendientes.put(proponenteNombre, nombreDestino);

        proponente.enviarMensaje("Sistema Gato: Propuesta enviada a " + nombreDestino + ". Esperando respuesta...");
        clienteDestino.enviarMensaje("Sistema Gato: ¡" + proponenteNombre + " te ha propuesto jugar al Gato! Usa /accept " + proponenteNombre + " o /reject " + proponenteNombre + ".");
    }

    private boolean tienePropuestaPendiente(String nombre1, String nombre2) {
        if (propuestasPendientes.getOrDefault(nombre1, "").equals(nombre2)) return true;
        if (propuestasPendientes.getOrDefault(nombre2, "").equals(nombre1)) return true;
        return false;
    }

    private void aceptarPropuesta(UnCliente aceptante, String nombreProponente) throws IOException {
        String aceptanteNombre = aceptante.getNombreCliente();

        if (nombreProponente.isEmpty()) {
            aceptante.enviarMensaje("Sistema Gato: Uso incorrecto. Usa /accept <usuario>");
            return;
        }

        String proponenteEsperado = propuestasPendientes.get(nombreProponente);

        if (proponenteEsperado == null || !proponenteEsperado.equals(aceptanteNombre)) {
            aceptante.enviarMensaje("Sistema Gato: El usuario '" + nombreProponente + "' no te ha propuesto un juego.");
            return;
        }

        UnCliente proponente = servidor.getCliente(nombreProponente);
        if (proponente == null) {
            aceptante.enviarMensaje("Sistema Gato: El proponente se ha desconectado. No se pudo iniciar el juego.");
            propuestasPendientes.remove(nombreProponente);
            return;
        }

        if (estaJugando(aceptanteNombre) || estaJugando(nombreProponente)) {
            aceptante.enviarMensaje("Sistema Gato: Tú o el proponente están ahora en una partida activa. No se puede iniciar otra.");
            propuestasPendientes.remove(nombreProponente);
            return;
        }

        propuestasPendientes.remove(nombreProponente);

        JuegoGato juego = new JuegoGato(proponente, aceptante);
        juegosActivos.put(nombreProponente, juego);
        juegosActivos.put(aceptanteNombre, juego);
    }

    private void rechazarPropuesta(UnCliente rechazante, String nombreProponente) throws IOException {
        String rechazanteNombre = rechazante.getNombreCliente();

        if (nombreProponente.isEmpty()) {
            rechazante.enviarMensaje("Sistema Gato: Uso incorrecto. Usa /reject <usuario>");
            return;
        }

        String proponenteEsperado = propuestasPendientes.get(nombreProponente);

        if (proponenteEsperado == null || !proponenteEsperado.equals(rechazanteNombre)) {
            rechazante.enviarMensaje("Sistema Gato: El usuario '" + nombreProponente + "' no te ha propuesto un juego.");
            return;
        }

        propuestasPendientes.remove(nombreProponente);

        UnCliente proponente = servidor.getCliente(nombreProponente);

        if (proponente != null) {
            proponente.enviarMensaje("Sistema Gato: " + rechazanteNombre + " ha rechazado tu propuesta de juego.");
        }
        rechazante.enviarMensaje("Sistema Gato: Has rechazado la propuesta de " + nombreProponente + ".");
    }

    private void manejarMovimiento(UnCliente cliente, String sFila, String sColumna) throws IOException {
        String clienteNombre = cliente.getNombreCliente();
        JuegoGato juego = getJuegoActivo(clienteNombre);

        if (juego == null) {
            cliente.enviarMensaje("Sistema Gato: No estás en un juego activo. Usa /gato <usuario> para proponer uno.");
            return;
        }

        if (juego.getEstado() != JuegoGato.EstadoJuego.ACTIVO) {
            cliente.enviarMensaje("Sistema Gato: El juego ha terminado (" + juego.getEstado().name() + "). Usa /gato para empezar uno nuevo.");
            removerJuego(clienteNombre, juego.getContrincante(cliente).getNombreCliente());
            return;
        }

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

    public void finalizarPorDesconexion(UnCliente desconectado) throws IOException {
        String nombreDesconectado = desconectado.getNombreCliente();

        JuegoGato juego = juegosActivos.get(nombreDesconectado);
        if (juego != null) {
            UnCliente oponente = juego.getContrincante(desconectado);
            String nombreOponente = oponente.getNombreCliente();

            juego.finalizarPorAbandono(desconectado);

            removerJuego(nombreDesconectado, nombreOponente);
        }

        cancelarPropuestasPendientes(nombreDesconectado);
    }

    private void cancelarPropuestasPendientes(String nombreDesconectado) throws IOException {
        String nombreDestino = propuestasPendientes.remove(nombreDesconectado);
        if (nombreDestino != null) {
            UnCliente destino = servidor.getCliente(nombreDestino);
            if (destino != null) {
                destino.enviarMensaje("Sistema Gato: La propuesta de juego de " + nombreDesconectado + " ha sido cancelada porque se ha desconectado.");
            }
        }

        for(Map.Entry<String, String> entry : propuestasPendientes.entrySet()) {
            if (entry.getValue().equals(nombreDesconectado)) {
                String nombreProponente = entry.getKey();
                propuestasPendientes.remove(nombreProponente);

                UnCliente proponente = servidor.getCliente(nombreProponente);
                if (proponente != null) {
                    proponente.enviarMensaje("Sistema Gato: La propuesta de juego a " + nombreDesconectado + " ha sido cancelada porque se ha desconectado.");
                }
                break;
            }
        }
    }

    private void removerJuego(String nombre1, String nombre2) {
        juegosActivos.remove(nombre1);
        juegosActivos.remove(nombre2);
    }
}