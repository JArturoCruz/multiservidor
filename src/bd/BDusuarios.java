package bd;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BDusuarios {

    private static final String DB_URL = "jdbc:sqlite:chat_users.db";
    private static final String TABLE_USERS = "USERS";
    private static final String TABLE_BLOCKS = "BLOCKS";
    // NUEVA TABLA: Para guardar las estadísticas de los juegos
    private static final String TABLE_RANKING = "RANKING_GATO";
    // NUEVA TABLA: Para guardar el historial de partidas entre dos jugadores
    private static final String TABLE_MATCHES = "MATCHES_GATO";

    static {
        inicializarBaseDeDatos();
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private static void inicializarBaseDeDatos() {
        String sqlUsers = "CREATE TABLE IF NOT EXISTS " + TABLE_USERS + " ("
                + "username TEXT PRIMARY KEY,"
                + "pin TEXT NOT NULL"
                + ");";

        String sqlBlocks = "CREATE TABLE IF NOT EXISTS " + TABLE_BLOCKS + " ("
                + "blocker_username TEXT NOT NULL,"
                + "blocked_username TEXT NOT NULL,"
                + "PRIMARY KEY (blocker_username, blocked_username),"
                + "FOREIGN KEY (blocker_username) REFERENCES " + TABLE_USERS + "(username),"
                + "FOREIGN KEY (blocked_username) REFERENCES " + TABLE_USERS + "(username)"
                + ");";

        // CREACIÓN DE LA TABLA DE RANKING
        String sqlRanking = "CREATE TABLE IF NOT EXISTS " + TABLE_RANKING + " ("
                + "username TEXT PRIMARY KEY,"
                + "victories INTEGER DEFAULT 0 NOT NULL,"
                + "defeats INTEGER DEFAULT 0 NOT NULL,"
                + "ties INTEGER DEFAULT 0 NOT NULL,"
                + "points INTEGER DEFAULT 0 NOT NULL,"
                + "FOREIGN KEY (username) REFERENCES " + TABLE_USERS + "(username)"
                + ");";

        // CREACIÓN DE LA TABLA DE HISTORIAL DE PARTIDAS
        String sqlMatches = "CREATE TABLE IF NOT EXISTS " + TABLE_MATCHES + " ("
                + "match_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "player1 TEXT NOT NULL,"
                + "player2 TEXT NOT NULL,"
                + "winner TEXT," // NULL for tie
                + "FOREIGN KEY (player1) REFERENCES " + TABLE_USERS + "(username),"
                + "FOREIGN KEY (player2) REFERENCES " + TABLE_USERS + "(username)"
                + ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(sqlUsers);
            stmt.execute(sqlBlocks);
            stmt.execute(sqlRanking); // Ejecutar creación de tabla RANKING
            stmt.execute(sqlMatches); // Ejecutar creación de tabla MATCHES
            System.out.println("Base de datos de usuarios (SQLite) inicializada. Tablas " + TABLE_USERS + ", " + TABLE_BLOCKS + ", " + TABLE_RANKING + " y " + TABLE_MATCHES + " listas.");
        } catch (SQLException e) {
            System.err.println("Error al inicializar la base de datos SQLite: " + e.getMessage());
        }
    }

    //---------------------------------------------------------
    // MÉTODOS EXISTENTES (omitiendo su contenido por brevedad)
    //---------------------------------------------------------

    public static boolean UsuarioExistente(String usuario) {
        String sql = "SELECT COUNT(*) FROM " + TABLE_USERS + " WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, usuario);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al verificar existencia de usuario: " + e.getMessage());
        }
        return false;
    }

    public static boolean RegistrarUsuario(String usuario, String pin) {
        if (UsuarioExistente(usuario)) {
            return false;
        }

        String sql = "INSERT INTO " + TABLE_USERS + "(username, pin) VALUES(?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, usuario);
            pstmt.setString(2, pin);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                System.out.println("Usuario '" + usuario + "' registrado en SQLite.");
                return true;
            }

        } catch (SQLException e) {
            System.err.println("Error al registrar el usuario en SQLite: " + e.getMessage());
        }
        return false;
    }

    public static boolean AutenticarUsuario(String usuario, String pin) {
        String sql = "SELECT pin FROM " + TABLE_USERS + " WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, usuario);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedPin = rs.getString("pin");
                    return storedPin.equals(pin);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al autenticar el usuario: " + e.getMessage());
        }
        return false;
    }

    public static boolean bloquearUsuario(String blockerUsername, String blockedUsername) {
        if (estaBloqueado(blockerUsername, blockedUsername)) {
            return true;
        }

        String sql = "INSERT INTO " + TABLE_BLOCKS + "(blocker_username, blocked_username) VALUES(?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, blockerUsername);
            pstmt.setString(2, blockedUsername);
            int affectedRows = pstmt.executeUpdate();

            return affectedRows > 0;

        } catch (SQLException e) {
            System.err.println("Error al bloquear el usuario: " + e.getMessage());
        }
        return false;
    }

    public static boolean desbloquearUsuario(String blockerUsername, String blockedUsername) {
        String sql = "DELETE FROM " + TABLE_BLOCKS + " WHERE blocker_username = ? AND blocked_username = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, blockerUsername);
            pstmt.setString(2, blockedUsername);
            int affectedRows = pstmt.executeUpdate();

            return affectedRows >= 0;

        } catch (SQLException e) {
            System.err.println("Error al desbloquear el usuario: " + e.getMessage());
        }
        return false;
    }

    public static boolean estaBloqueado(String blockerUsername, String blockedUsername) {
        String sql = "SELECT COUNT(*) FROM " + TABLE_BLOCKS + " WHERE blocker_username = ? AND blocked_username = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, blockerUsername);
            pstmt.setString(2, blockedUsername);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al verificar bloqueo: " + e.getMessage());
        }
        return false;
    }

    //---------------------------------------------------------
    // NUEVOS MÉTODOS DE RANKING Y PARTIDAS
    //---------------------------------------------------------

    // Clase auxiliar para el ranking
    public static class RankingEntry {
        public String username;
        public int victories;
        public int defeats;
        public int ties;
        public int points;
    }

    // Puntos: Victoria = 2, Empate = 1, Derrota = 0
    public static final int PUNTOS_VICTORIA = 2;
    public static final int PUNTOS_EMPATE = 1;
    public static final int PUNTOS_DERROTA = 0;

    // 1. Método para registrar el resultado de una partida
    public static void registrarResultadoPartida(String jugador1, String jugador2, String ganador) {
        // En SQLite, usamos una transacción para asegurar la consistencia
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            // a) Actualizar estadísticas de ranking
            if (ganador != null) {
                // Ganador
                actualizarEstadisticas(conn, ganador, "victories", 1, PUNTOS_VICTORIA);
                // Perdedor
                String perdedor = ganador.equals(jugador1) ? jugador2 : jugador1;
                actualizarEstadisticas(conn, perdedor, "defeats", 1, PUNTOS_DERROTA);
            } else {
                // Empate
                actualizarEstadisticas(conn, jugador1, "ties", 1, PUNTOS_EMPATE);
                actualizarEstadisticas(conn, jugador2, "ties", 1, PUNTOS_EMPATE);
            }

            // b) Registrar en la tabla de historial de partidas
            registrarMatch(conn, jugador1, jugador2, ganador);

            conn.commit();
        } catch (SQLException e) {
            System.err.println("Error al registrar el resultado de la partida: " + e.getMessage());
        }
    }

    // Método auxiliar para actualizar las estadísticas de un solo jugador
    private static void actualizarEstadisticas(Connection conn, String usuario, String columna, int cambioEst, int cambioPuntos) throws SQLException {
        // UPSERT (UPDATE or INSERT) para ranking
        String sqlUpsert = "INSERT INTO " + TABLE_RANKING + " (username, " + columna + ", points) VALUES (?, ?, ?)"
                + " ON CONFLICT(username) DO UPDATE SET "
                + columna + " = " + columna + " + ?, points = points + ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sqlUpsert)) {
            pstmt.setString(1, usuario);
            pstmt.setInt(2, cambioEst);
            pstmt.setInt(3, cambioPuntos);
            pstmt.setInt(4, cambioEst);
            pstmt.setInt(5, cambioPuntos);
            pstmt.executeUpdate();
        }
    }

    // Método auxiliar para registrar el match en la tabla de historial
    private static void registrarMatch(Connection conn, String p1, String p2, String winner) throws SQLException {
        String sql = "INSERT INTO " + TABLE_MATCHES + "(player1, player2, winner) VALUES(?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, p1);
            pstmt.setString(2, p2);
            pstmt.setString(3, winner);
            pstmt.executeUpdate();
        }
    }

    // 2. Método para obtener el ranking general
    public static List<RankingEntry> obtenerRankingGeneral() {
        List<RankingEntry> ranking = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE_RANKING + " ORDER BY points DESC, victories DESC, ties DESC";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                RankingEntry entry = new RankingEntry();
                entry.username = rs.getString("username");
                entry.victories = rs.getInt("victories");
                entry.defeats = rs.getInt("defeats");
                entry.ties = rs.getInt("ties");
                entry.points = rs.getInt("points");
                ranking.add(entry);
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener el ranking: " + e.getMessage());
        }
        return ranking;
    }

    // 3. Método para obtener estadísticas (victorias/derrotas/empates) entre dos jugadores
    public static Map<String, Integer> obtenerEstadisticasVs(String user1, String user2) {
        Map<String, Integer> stats = new HashMap<>();
        String sql = "SELECT winner FROM " + TABLE_MATCHES +
                " WHERE (player1 = ? AND player2 = ?) OR (player1 = ? AND player2 = ?)";

        stats.put("total", 0);
        stats.put(user1 + "_wins", 0);
        stats.put(user2 + "_wins", 0);
        stats.put("ties", 0);

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user1);
            pstmt.setString(2, user2);
            pstmt.setString(3, user2);
            pstmt.setString(4, user1);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    stats.put("total", stats.get("total") + 1);
                    String winner = rs.getString("winner");
                    if (winner == null) {
                        stats.put("ties", stats.get("ties") + 1);
                    } else if (winner.equals(user1)) {
                        stats.put(user1 + "_wins", stats.get(user1 + "_wins") + 1);
                    } else if (winner.equals(user2)) {
                        stats.put(user2 + "_wins", stats.get(user2 + "_wins") + 1);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener estadísticas VS: " + e.getMessage());
        }
        return stats;
    }
}