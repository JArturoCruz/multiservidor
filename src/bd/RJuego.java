package bd;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RJuego {

    private static final String TABLE_RANKING = "RANKING_GATO";
    private static final String TABLE_MATCHES = "MATCHES_GATO";

    public static class RankingEntry {
        public String username;
        public int victories, defeats, ties, points;
    }

    public static final int PUNTOS_VICTORIA = 2;
    public static final int PUNTOS_EMPATE = 1;
    public static final int PUNTOS_DERROTA = 0;

    public static void crearTablas(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_RANKING + " ("
                + "username TEXT PRIMARY KEY, victories INTEGER DEFAULT 0 NOT NULL,"
                + "defeats INTEGER DEFAULT 0 NOT NULL, ties INTEGER DEFAULT 0 NOT NULL,"
                + "points INTEGER DEFAULT 0 NOT NULL,"
                + "FOREIGN KEY (username) REFERENCES USERS(username));");
        stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_MATCHES + " ("
                + "match_id INTEGER PRIMARY KEY AUTOINCREMENT, player1 TEXT NOT NULL,"
                + "player2 TEXT NOT NULL, winner TEXT,"
                + "FOREIGN KEY (player1) REFERENCES USERS(username),"
                + "FOREIGN KEY (player2) REFERENCES USERS(username));");
    }

    public static void registrarResultadoPartida(String jugador1, String jugador2, String ganador) {
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            if (ganador != null) {
                actualizarGanadorPerdedor(conn, ganador, (ganador.equals(jugador1) ? jugador2 : jugador1));
            } else {
                actualizarEmpate(conn, jugador1, jugador2);
            }
            registrarMatch(conn, jugador1, jugador2, ganador);
            conn.commit();
        } catch (SQLException e) {
            System.err.println("Error al registrar el resultado de la partida: " + e.getMessage());
        }
    }

    private static void actualizarGanadorPerdedor(Connection conn, String ganador, String perdedor) throws SQLException {
        actualizarEstadisticas(conn, ganador, "victories", 1, PUNTOS_VICTORIA);
        actualizarEstadisticas(conn, perdedor, "defeats", 1, PUNTOS_DERROTA);
    }

    private static void actualizarEmpate(Connection conn, String j1, String j2) throws SQLException {
        actualizarEstadisticas(conn, j1, "ties", 1, PUNTOS_EMPATE);
        actualizarEstadisticas(conn, j2, "ties", 1, PUNTOS_EMPATE);
    }

    private static void actualizarEstadisticas(Connection conn, String u, String col, int cEst, int cPts) throws SQLException {
        String sql = "INSERT INTO " + TABLE_RANKING + " (username, " + col + ", points) VALUES (?, ?, ?)"
                + " ON CONFLICT(username) DO UPDATE SET " + col + " = " + col + " + ?, points = points + ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, u); pstmt.setInt(2, cEst); pstmt.setInt(3, cPts);
            pstmt.setInt(4, cEst); pstmt.setInt(5, cPts);
            pstmt.executeUpdate();
        }
    }

    private static void registrarMatch(Connection conn, String p1, String p2, String winner) throws SQLException {
        String sql = "INSERT INTO " + TABLE_MATCHES + "(player1, player2, winner) VALUES(?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, p1); pstmt.setString(2, p2); pstmt.setString(3, winner);
            pstmt.executeUpdate();
        }
    }

    public static List<RankingEntry> obtenerRankingGeneral() {
        List<RankingEntry> ranking = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE_RANKING + " ORDER BY points DESC, victories DESC, ties DESC LIMIT 10";
        try (Connection conn = DatabaseManager.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) ranking.add(mapearRankingEntry(rs));
        } catch (SQLException e) {
            System.err.println("Error al obtener el ranking: " + e.getMessage());
        }
        return ranking;
    }

    private static RankingEntry mapearRankingEntry(ResultSet rs) throws SQLException {
        RankingEntry entry = new RankingEntry();
        entry.username = rs.getString("username"); entry.victories = rs.getInt("victories");
        entry.defeats = rs.getInt("defeats"); entry.ties = rs.getInt("ties");
        entry.points = rs.getInt("points");
        return entry;
    }

    public static Map<String, Integer> obtenerEstadisticasVs(String user1, String user2) {
        Map<String, Integer> stats = inicializarStatsVs(user1, user2);
        String sql = "SELECT winner FROM " + TABLE_MATCHES + " WHERE (player1 = ? AND player2 = ?) OR (player1 = ? AND player2 = ?)";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user1); pstmt.setString(2, user2);
            pstmt.setString(3, user2); pstmt.setString(4, user1);
            try (ResultSet rs = pstmt.executeQuery()) {
                procesarResultadosVs(rs, stats, user1, user2);
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener estadísticas VS: " + e.getMessage());
        }
        return stats;
    }

    private static Map<String, Integer> inicializarStatsVs(String user1, String user2) {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("total", 0); stats.put(user1 + "_wins", 0);
        stats.put(user2 + "_wins", 0); stats.put("ties", 0);
        return stats;
    }

    private static void procesarResultadosVs(ResultSet rs, Map<String, Integer> stats, String user1, String user2) throws SQLException {
        while (rs.next()) {
            stats.put("total", stats.get("total") + 1);
            String winner = rs.getString("winner");
            if (winner == null) {
                stats.put("ties", stats.get("ties") + 1);
            } else if (winner.equalsIgnoreCase(user1)) {
                stats.put(user1 + "_wins", stats.get(user1 + "_wins") + 1);
            } else if (winner.equalsIgnoreCase(user2)) {
                stats.put(user2 + "_wins", stats.get(user2 + "_wins") + 1);
            }
        }
    }
}