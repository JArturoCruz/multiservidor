package juego;

import servidormulti.UnCliente;
import java.io.IOException;
import java.util.Random;

public class JuegoGato {
    public enum EstadoCasilla { VACIO, X, O }
    public enum EstadoJuego { ACTIVO, GANA_X, GANA_O, EMPATE, ABANDONO }

    private final UnCliente jugadorX;
    private final UnCliente jugadorO;
    private final EstadoCasilla[][] tablero;
    private UnCliente turnoActual;
    private EstadoJuego estado;

    public JuegoGato(UnCliente c1, UnCliente c2) throws IOException {
        this.tablero = inicializarTablero();
        this.estado = EstadoJuego.ACTIVO;

        UnCliente[] jugadores = asignarSimbolos(c1, c2);
        this.jugadorX = jugadores[0];
        this.jugadorO = jugadores[1];

        this.turnoActual = asignarTurnoInicial(jugadores);
        notificarInicio();
    }

    private EstadoCasilla[][] inicializarTablero() {
        EstadoCasilla[][] t = new EstadoCasilla[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                t[i][j] = EstadoCasilla.VACIO;
            }
        }
        return t;
    }

    private UnCliente[] asignarSimbolos(UnCliente c1, UnCliente c2) {
        Random rand = new Random();
        if (rand.nextBoolean()) {
            return new UnCliente[]{c1, c2};
        } else {
            return new UnCliente[]{c2, c1};
        }
    }

    private UnCliente asignarTurnoInicial(UnCliente[] jugadores) {
        Random rand = new Random();
        return rand.nextBoolean() ? jugadores[0] : jugadores[1];
    }

    public UnCliente getJugadorX() { return jugadorX; }
    public UnCliente getJugadorO() { return jugadorO; }
    public UnCliente getTurnoActual() { return turnoActual; }
    public EstadoJuego getEstado() { return estado; }

    public UnCliente getContrincante(UnCliente cliente) {
        if (cliente == jugadorX) return jugadorO;
        if (cliente == jugadorO) return jugadorX;
        return null;
    }

    private String getSimbolo(UnCliente cliente) {
        if (cliente == jugadorX) return "X";
        if (cliente == jugadorO) return "O";
        return "?";
    }

    private String dibujarTablero() {
        StringBuilder sb = new StringBuilder("\n  1 2 3\n");
        for (int i = 0; i < 3; i++) {
            sb.append((i + 1)).append(" ");
            dibujarFila(sb, i);
        }
        return sb.toString();
    }

    private String obtenerSimboloCasilla(int i, int j) {
        switch (tablero[i][j]) {
            case X: return "X";
            case O: return "O";
            default: return "-";
        }
    }

    private void dibujarFila(StringBuilder sb, int i) {
        for (int j = 0; j < 3; j++) {
            sb.append(obtenerSimboloCasilla(i, j));
            if (j < 2) sb.append("|");
        }
        sb.append("\n");
        if (i < 2) sb.append("  -----\n");
    }

    private boolean verificarGanador(EstadoCasilla simbolo) {
        for (int i = 0; i < 3; i++) {
            if (verificarLinea(i, simbolo) || verificarColumna(i, simbolo)) return true;
        }
        return verificarDiagonales(simbolo);
    }

    private boolean verificarLinea(int i, EstadoCasilla simbolo) {
        return tablero[i][0] == simbolo && tablero[i][1] == simbolo && tablero[i][2] == simbolo;
    }

    private boolean verificarColumna(int i, EstadoCasilla simbolo) {
        return tablero[0][i] == simbolo && tablero[1][i] == simbolo && tablero[2][i] == simbolo;
    }

    private boolean verificarDiagonales(EstadoCasilla simbolo) {
        return (tablero[0][0] == simbolo && tablero[1][1] == simbolo && tablero[2][2] == simbolo) ||
                (tablero[0][2] == simbolo && tablero[1][1] == simbolo && tablero[2][0] == simbolo);
    }

    private boolean verificarEmpate() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (tablero[i][j] == EstadoCasilla.VACIO) {
                    return false;
                }
            }
        }
        return true;
    }

    public void notificarInicio() throws IOException {
        String infoX = jugadorX.getNombreCliente() + " (X)";
        String infoO = jugadorO.getNombreCliente() + " (O)";
        String mensajeGeneral = "Sistema Gato: ¡Juego iniciado! " + infoX + " vs " + infoO + ".\n";
        String mensajeTurno = "Turno de " + turnoActual.getNombreCliente() + " (" + getSimbolo(turnoActual) + "). Usa /move 1 3";

        jugadorX.enviarMensaje(mensajeGeneral + "Tú eres X." + dibujarTablero() + mensajeTurno);
        jugadorO.enviarMensaje(mensajeGeneral + "Tú eres O." + dibujarTablero() + mensajeTurno);
    }

    private void notificarMovimiento(String mensaje) throws IOException {
        jugadorX.enviarMensaje(mensaje + dibujarTablero());
        jugadorO.enviarMensaje(mensaje + dibujarTablero());
    }

    private void notificarResultado(String mensaje) throws IOException {
        jugadorX.enviarMensaje(mensaje + dibujarTablero());
        jugadorO.enviarMensaje(mensaje + dibujarTablero());
    }

    public boolean realizarMovimiento(UnCliente cliente, int fila, int columna) throws IOException {
        if (!validarMovimiento(cliente, fila, columna)) return false;

        realizarCambio(cliente, fila, columna);

        if (verificarFinDeJuego(cliente, fila, columna)) return true;

        cambiarTurnoYNotificar(cliente);
        return false;
    }

    private boolean validarMovimiento(UnCliente cliente, int fila, int columna) throws IOException {
        if (cliente != turnoActual) {
            cliente.enviarMensaje("Sistema Gato: No es tu turno.");
            return false;
        }
        if (fila < 1 || fila > 3 || columna < 1 || columna > 3) {
            cliente.enviarMensaje("Sistema Gato: Movimiento inválido. Usa /move <fila> <columna> (1-3).");
            return false;
        }
        if (tablero[fila - 1][columna - 1] != EstadoCasilla.VACIO) {
            cliente.enviarMensaje("Sistema Gato: Posición (" + fila + "," + columna + ") ya ocupada.");
            return false;
        }
        return true;
    }

    private void realizarCambio(UnCliente cliente, int fila, int columna) {
        EstadoCasilla simbolo = (cliente == jugadorX) ? EstadoCasilla.X : EstadoCasilla.O;
        tablero[fila - 1][columna - 1] = simbolo;
    }

    private boolean verificarFinDeJuego(UnCliente cliente, int fila, int columna) throws IOException {
        EstadoCasilla simbolo = (cliente == jugadorX) ? EstadoCasilla.X : EstadoCasilla.O;
        String notif = "Sistema Gato: " + cliente.getNombreCliente() + " jugó en (" + fila + "," + columna + ").";

        if (verificarGanador(simbolo)) {
            estado = (simbolo == EstadoCasilla.X) ? EstadoJuego.GANA_X : EstadoJuego.GANA_O;
            notificarResultado(notif + "\nSistema Gato: ¡" + cliente.getNombreCliente() + " ha ganado!");
            return true;
        } else if (verificarEmpate()) {
            estado = EstadoJuego.EMPATE;
            notificarResultado(notif + "\nSistema Gato: ¡Es un empate!");
            return true;
        }
        return false;
    }

    private void cambiarTurnoYNotificar(UnCliente cliente) throws IOException {
        String notif = "Sistema Gato: " + cliente.getNombreCliente() + " ha jugado.";
        turnoActual = getContrincante(cliente);
        notif += "\nTurno de " + turnoActual.getNombreCliente() + " (" + getSimbolo(turnoActual) + ").";
        notificarMovimiento(notif);
    }

    public void finalizarPorAbandono(UnCliente desconectado) throws IOException {
        if (estado != EstadoJuego.ACTIVO) return;

        estado = EstadoJuego.ABANDONO;
        UnCliente oponente = getContrincante(desconectado);

        if (oponente != null) {
            oponente.enviarMensaje("Sistema Gato: ¡" + desconectado.getNombreCliente() + " se ha desconectado! Has ganado automáticamente el juego. La partida ha finalizado.");
        }
    }
}