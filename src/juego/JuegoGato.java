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
        this.tablero = new EstadoCasilla[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                tablero[i][j] = EstadoCasilla.VACIO;
            }
        }
        this.estado = EstadoJuego.ACTIVO;

        Random rand = new Random();
        if (rand.nextBoolean()) {
            this.jugadorX = c1;
            this.jugadorO = c2;
        } else {
            this.jugadorX = c2;
            this.jugadorO = c1;
        }

        this.turnoActual = rand.nextBoolean() ? jugadorX : jugadorO;

        notificarInicio();
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
            for (int j = 0; j < 3; j++) {
                switch (tablero[i][j]) {
                    case X: sb.append("X"); break;
                    case O: sb.append("O"); break;
                    case VACIO: sb.append("-"); break;
                }
                if (j < 2) sb.append("|");
            }
            sb.append("\n");
            if (i < 2) sb.append("  -----\n");
        }
        return sb.toString();
    }

    private boolean verificarGanador(EstadoCasilla simbolo) {
        for (int i = 0; i < 3; i++) {
            if ((tablero[i][0] == simbolo && tablero[i][1] == simbolo && tablero[i][2] == simbolo) ||
                    (tablero[0][i] == simbolo && tablero[1][i] == simbolo && tablero[2][i] == simbolo)) {
                return true;
            }
        }
        if ((tablero[0][0] == simbolo && tablero[1][1] == simbolo && tablero[2][2] == simbolo) ||
                (tablero[0][2] == simbolo && tablero[1][1] == simbolo && tablero[2][0] == simbolo)) {
            return true;
        }
        return false;
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
        String mensajeTurno = "Turno de " + turnoActual.getNombreCliente() + " (" + getSimbolo(turnoActual) + "). Usa /move <fila> <columna> (ej: /move 1 3)";

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
        if (cliente != turnoActual) {
            cliente.enviarMensaje("Sistema Gato: No es tu turno.");
            return false;
        }

        if (fila < 1 || fila > 3 || columna < 1 || columna > 3) {
            cliente.enviarMensaje("Sistema Gato: Movimiento inválido. Usa /move <fila> <columna> (fila y columna deben ser 1, 2 o 3).");
            return false;
        }

        int r = fila - 1;
        int c = columna - 1;

        if (tablero[r][c] != EstadoCasilla.VACIO) {
            cliente.enviarMensaje("Sistema Gato: La posición (" + fila + "," + columna + ") ya está ocupada.");
            return false;
        }

        EstadoCasilla simbolo = (cliente == jugadorX) ? EstadoCasilla.X : EstadoCasilla.O;
        tablero[r][c] = simbolo;

        String notificacion = "Sistema Gato: " + cliente.getNombreCliente() + " ha jugado en (" + fila + "," + columna + ").";

        if (verificarGanador(simbolo)) {
            estado = (simbolo == EstadoCasilla.X) ? EstadoJuego.GANA_X : EstadoJuego.GANA_O;
            notificarResultado(notificacion + "\nSistema Gato: ¡" + cliente.getNombreCliente() + " (" + getSimbolo(cliente) + ") ha ganado!");
            return true;
        } else if (verificarEmpate()) {
            estado = EstadoJuego.EMPATE;
            notificarResultado(notificacion + "\nSistema Gato: ¡Es un empate!");
            return true;
        }

        turnoActual = getContrincante(cliente);
        notificacion += "\nTurno de " + turnoActual.getNombreCliente() + " (" + getSimbolo(turnoActual) + ").";
        notificarMovimiento(notificacion);

        return false;
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