package bd;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BDusuarios {

    private static final String DB_URL = "jdbc:sqlite:chat_users.db";
    private static final String TABLE_USERS = "USERS";
    private static final String TABLE_BLOCKS = "BLOCKS";
    private static final String TABLE_RANKING = "RANKING_GATO";
    private static final String TABLE_MATCHES = "MATCHES_GATO";
    private static final String TABLE_GROUPS = "GROUPS";
    private static final String TABLE_GROUP_MEMBERS = "GROUP_MEMBERS";
    private static final String TABLE_GROUP_MESSAGES = "GROUP_MESSAGES";
    private static final String TABLE_USER_LAST_SEEN = "USER_LAST_SEEN";
    public static final int ID_TODOS = 1;
    public static final String NOMBRE_TODOS = "Todos";
    public static final int PUNTOS_VICTORIA = 2;
    public static final int PUNTOS_EMPATE = 1;
    public static final int PUNTOS_DERROTA = 0;

    static {
        inicializarBaseDeDatos();
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private static void inicializarBaseDeDatos() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            crearTablasUsuarios(stmt);
            crearTablasJuego(stmt);
            crearTablasGrupos(stmt);
            System.out.println("Base de datos (SQLite) inicializada.");
        } catch (SQLException e) {
            System.err.println("Error al inicializar la base de datos SQLite: " + e.getMessage());
        }
    }

    private static void crearTablasUsuarios(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_USERS + " (username TEXT PRIMARY KEY, pin TEXT NOT NULL);");
        stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_BLOCKS + " ("
                + "blocker_username TEXT NOT NULL, blocked_username TEXT NOT NULL,"
                + "PRIMARY KEY (blocker_username, blocked_username),"
                + "FOREIGN KEY (blocker_username) REFERENCES " + TABLE_USERS + "(username),"
                + "FOREIGN KEY (blocked_username) REFERENCES " + TABLE_USERS + "(username));");
    }

    private static void crearTablasJuego(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_RANKING + " ("
                + "username TEXT PRIMARY KEY, victories INTEGER DEFAULT 0 NOT NULL,"
                + "defeats INTEGER DEFAULT 0 NOT NULL, ties INTEGER DEFAULT 0 NOT NULL,"
                + "points INTEGER DEFAULT 0 NOT NULL,"
                + "FOREIGN KEY (username) REFERENCES " + TABLE_USERS + "(username));");
        stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_MATCHES + " ("
                + "match_id INTEGER PRIMARY KEY AUTOINCREMENT, player1 TEXT NOT NULL,"
                + "player2 TEXT NOT NULL, winner TEXT,"
                + "FOREIGN KEY (player1) REFERENCES " + TABLE_USERS + "(username),"
                + "FOREIGN KEY (player2) REFERENCES " + TABLE_USERS + "(username));");
    }

    private static void crearTablasGrupos(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_GROUPS + " (group_id INTEGER PRIMARY KEY AUTOINCREMENT, group_name TEXT UNIQUE NOT NULL);");
        stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_GROUP_MEMBERS + " ("
                + "group_id INTEGER NOT NULL, username TEXT NOT NULL, PRIMARY KEY (group_id, username),"
                + "FOREIGN KEY (group_id) REFERENCES " + TABLE_GROUPS + "(group_id) ON DELETE CASCADE,"
                + "FOREIGN KEY (username) REFERENCES " + TABLE_USERS + "(username));");
        stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_GROUP_MESSAGES + " ("
                + "message_id INTEGER PRIMARY KEY AUTOINCREMENT, group_id INTEGER NOT NULL,"
                + "sender_username TEXT NOT NULL, content TEXT NOT NULL, timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY (group_id) REFERENCES " + TABLE_GROUPS + "(group_id) ON DELETE CASCADE,"
                + "FOREIGN KEY (sender_username) REFERENCES " + TABLE_USERS + "(username));");
        stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_USER_LAST_SEEN + " ("
                + "username TEXT NOT NULL, group_id INTEGER NOT NULL, last_message_id INTEGER NOT NULL,"
                + "PRIMARY KEY (username, group_id),"
                + "FOREIGN KEY (username) REFERENCES " + TABLE_USERS + "(username),"
                + "FOREIGN KEY (group_id) REFERENCES " + TABLE_GROUPS + "(group_id) ON DELETE CASCADE);");
        stmt.execute("INSERT OR IGNORE INTO " + TABLE_GROUPS + " (group_id, group_name) VALUES (" + ID_TODOS + ", '" + NOMBRE_TODOS + "')");
    }

    public static boolean UsuarioExistente(String usuario) {
        String sql = "SELECT COUNT(*) FROM " + TABLE_USERS + " WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuario);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error al verificar existencia de usuario: " + e.getMessage());
            return false;
        }
    }

    public static boolean RegistrarUsuario(String usuario, String pin) {
        if (UsuarioExistente(usuario)) return false;
        String sql = "INSERT INTO " + TABLE_USERS + "(username, pin) VALUES(?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuario);
            pstmt.setString(2, pin);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) System.out.println("Usuario '" + usuario + "' registrado en SQLite.");
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("Error al registrar el usuario en SQLite: " + e.getMessage());
            return false;
        }
    }

    public static boolean AutenticarUsuario(String usuario, String pin) {
        String sql = "SELECT pin FROM " + TABLE_USERS + " WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuario);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getString("pin").equals(pin);
            }
        } catch (SQLException e) {
            System.err.println("Error al autenticar el usuario: " + e.getMessage());
            return false;
        }
    }

    public static boolean bloquearUsuario(String blocker, String blocked) {
        if (estaBloqueado(blocker, blocked)) return true;
        String sql = "INSERT INTO " + TABLE_BLOCKS + "(blocker_username, blocked_username) VALUES(?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, blocker);
            pstmt.setString(2, blocked);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error al bloquear el usuario: " + e.getMessage());
            return false;
        }
    }

    public static boolean desbloquearUsuario(String blocker, String blocked) {
        String sql = "DELETE FROM " + TABLE_BLOCKS + " WHERE blocker_username = ? AND blocked_username = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, blocker);
            pstmt.setString(2, blocked);
            return pstmt.executeUpdate() >= 0;
        } catch (SQLException e) {
            System.err.println("Error al desbloquear el usuario: " + e.getMessage());
            return false;
        }
    }

    public static boolean estaBloqueado(String blocker, String blocked) {
        if (blocker.startsWith("anonimo") || blocked.startsWith("anonimo")) return false;
        String sql = "SELECT COUNT(*) FROM " + TABLE_BLOCKS + " WHERE blocker_username = ? AND blocked_username = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, blocker);
            pstmt.setString(2, blocked);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error al verificar bloqueo: " + e.getMessage());
            return false;
        }
    }

    public static class RankingEntry {
        public String username;
        public int victories, defeats, ties, points;
    }

    public static void registrarResultadoPartida(String jugador1, String jugador2, String ganador) {
        try (Connection conn = getConnection()) {
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
            pstmt.setString(1, u);
            pstmt.setInt(2, cEst);
            pstmt.setInt(3, cPts);
            pstmt.setInt(4, cEst);
            pstmt.setInt(5, cPts);
            pstmt.executeUpdate();
        }
    }

    private static void registrarMatch(Connection conn, String p1, String p2, String winner) throws SQLException {
        String sql = "INSERT INTO " + TABLE_MATCHES + "(player1, player2, winner) VALUES(?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, p1);
            pstmt.setString(2, p2);
            pstmt.setString(3, winner);
            pstmt.executeUpdate();
        }
    }

    public static List<RankingEntry> obtenerRankingGeneral() {
        List<RankingEntry> ranking = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE_RANKING + " ORDER BY points DESC, victories DESC, ties DESC LIMIT 10";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ranking.add(mapearRankingEntry(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener el ranking: " + e.getMessage());
        }
        return ranking;
    }

    private static RankingEntry mapearRankingEntry(ResultSet rs) throws SQLException {
        RankingEntry entry = new RankingEntry();
        entry.username = rs.getString("username");
        entry.victories = rs.getInt("victories");
        entry.defeats = rs.getInt("defeats");
        entry.ties = rs.getInt("ties");
        entry.points = rs.getInt("points");
        return entry;
    }

    public static Map<String, Integer> obtenerEstadisticasVs(String user1, String user2) {
        Map<String, Integer> stats = inicializarStatsVs(user1, user2);
        String sql = "SELECT winner FROM " + TABLE_MATCHES + " WHERE (player1 = ? AND player2 = ?) OR (player1 = ? AND player2 = ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user1);
            pstmt.setString(2, user2);
            pstmt.setString(3, user2);
            pstmt.setString(4, user1);
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
        stats.put("total", 0);
        stats.put(user1 + "_wins", 0);
        stats.put(user2 + "_wins", 0);
        stats.put("ties", 0);
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

    public static int obtenerGrupoIdPorNombre(String groupName) {
        String sql = "SELECT group_id FROM " + TABLE_GROUPS + " WHERE group_name = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, groupName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener ID de grupo: " + e.getMessage());
            return -1;
        }
    }

    public static boolean crearGrupo(String groupName, String creator) {
        if (obtenerGrupoIdPorNombre(groupName) != -1) return false;
        String sqlGrupo = "INSERT INTO " + TABLE_GROUPS + " (group_name) VALUES (?)";
        String sqlMiembro = "INSERT INTO " + TABLE_GROUP_MEMBERS + " (group_id, username) VALUES (?, ?)";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            int groupId = ejecutarInsertGrupo(conn, sqlGrupo, groupName);
            ejecutarInsertMiembro(conn, sqlMiembro, groupId, creator);
            conn.commit();
            return true;
        } catch (SQLException e) {
            System.err.println("Error al crear grupo (rollback): " + e.getMessage());
            return false;
        }
    }

    private static int ejecutarInsertGrupo(Connection conn, String sql, String name) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, name);
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
                throw new SQLException("Fallo al crear grupo, no se obtuvo ID.");
            }
        }
    }

    private static void ejecutarInsertMiembro(Connection conn, String sql, int gId, String user) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, gId);
            pstmt.setString(2, user);
            pstmt.executeUpdate();
        }
    }

    public static boolean eliminarGrupo(String groupName) {
        if (groupName.equalsIgnoreCase(NOMBRE_TODOS)) return false;
        String sql = "DELETE FROM " + TABLE_GROUPS + " WHERE group_name = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, groupName);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error al eliminar grupo: " + e.getMessage());
            return false;
        }
    }

    public static boolean unirUsuarioAGrupo(String username, int groupId) {
        if (groupId == ID_TODOS) return true;
        String sql = "INSERT OR IGNORE INTO " + TABLE_GROUP_MEMBERS + " (group_id, username) VALUES (?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error al unir usuario a grupo: " + e.getMessage());
            return false;
        }
    }

    public static List<String> obtenerMiembrosGrupo(int groupId) {
        List<String> miembros = new ArrayList<>();
        if (groupId == ID_TODOS) return miembros; // "Todos" es virtual
        String sql = "SELECT username FROM " + TABLE_GROUP_MEMBERS + " WHERE group_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) miembros.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener miembros de grupo: " + e.getMessage());
        }
        return miembros;
    }

    public static List<String> obtenerTodosLosGrupos() {
        List<String> grupos = new ArrayList<>();
        String sql = "SELECT group_name FROM " + TABLE_GROUPS + " ORDER BY group_name";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) grupos.add(rs.getString("group_name"));
        } catch (SQLException e) {
            System.err.println("Error al obtener lista de grupos: " + e.getMessage());
        }
        return grupos;
    }

    public static long guardarMensajeGrupo(int gId, String sender, String content) {
        String sql = "INSERT INTO " + TABLE_GROUP_MESSAGES + " (group_id, sender_username, content) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, gId);
            pstmt.setString(2, sender);
            pstmt.setString(3, content);
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            System.err.println("Error al guardar mensaje de grupo: " + e.getMessage());
            return -1;
        }
    }


    public static class MensajeGrupo {
        public long messageId;
        public String sender, content, timestamp;
        MensajeGrupo(long id, String s, String c, String ts) { this.messageId = id; this.sender = s; this.content = c; this.timestamp = ts; }
    }

    public static List<MensajeGrupo> obtenerMensajesNoVistos(String username, int groupId) {
        long lastSeenId = obtenerLastSeenId(username, groupId);
        return obtenerNuevosMensajesDesde(groupId, lastSeenId);
    }

    private static long obtenerLastSeenId(String username, int groupId) {
        String sql = "SELECT last_message_id FROM " + TABLE_USER_LAST_SEEN + " WHERE username = ? AND group_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setInt(2, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener last_seen_id: " + e.getMessage());
            return 0;
        }
    }

    private static List<MensajeGrupo> obtenerNuevosMensajesDesde(int groupId, long lastSeenId) {
        List<MensajeGrupo> mensajes = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE_GROUP_MESSAGES + " WHERE group_id = ? AND message_id > ? ORDER BY timestamp ASC";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setLong(2, lastSeenId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    mensajes.add(new MensajeGrupo(rs.getLong("message_id"), rs.getString("sender_username"),
                            rs.getString("content"), rs.getString("timestamp")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener mensajes no vistos: " + e.getMessage());
        }
        return mensajes;
    }

    public static void actualizarUltimoMensajeVisto(String username, int groupId, long messageId) {
        if (username.startsWith("anonimo")) return;
        String sql = "INSERT INTO " + TABLE_USER_LAST_SEEN + " (username, group_id, last_message_id) VALUES (?, ?, ?)" +
                " ON CONFLICT(username, group_id) DO UPDATE SET last_message_id = excluded.last_message_id";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setInt(2, groupId);
            pstmt.setLong(3, messageId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al actualizar último mensaje visto: " + e.getMessage());
        }
    }
}