package bd;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RGrupos {

    private static final String TABLE_GROUPS = "GROUPS";
    private static final String TABLE_GROUP_MEMBERS = "GROUP_MEMBERS";
    private static final String TABLE_GROUP_MESSAGES = "GROUP_MESSAGES";
    private static final String TABLE_USER_LAST_SEEN = "USER_LAST_SEEN";

    public static final int ID_TODOS = 1;
    public static final String NOMBRE_TODOS = "Todos";

    public static class MensajeGrupo {
        public long messageId;
        public String sender, content, timestamp;
        public MensajeGrupo(long id, String s, String c, String ts) { this.messageId = id; this.sender = s; this.content = c; this.timestamp = ts; }
    }

    public static void crearTablas(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_GROUPS + " (group_id INTEGER PRIMARY KEY AUTOINCREMENT, group_name TEXT UNIQUE NOT NULL);");
        stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_GROUP_MEMBERS + " ("
                + "group_id INTEGER NOT NULL, username TEXT NOT NULL, PRIMARY KEY (group_id, username),"
                + "FOREIGN KEY (group_id) REFERENCES " + TABLE_GROUPS + "(group_id) ON DELETE CASCADE,"
                + "FOREIGN KEY (username) REFERENCES USERS(username));");
        stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_GROUP_MESSAGES + " ("
                + "message_id INTEGER PRIMARY KEY AUTOINCREMENT, group_id INTEGER NOT NULL,"
                + "sender_username TEXT NOT NULL, content TEXT NOT NULL, timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY (group_id) REFERENCES " + TABLE_GROUPS + "(group_id) ON DELETE CASCADE,"
                + "FOREIGN KEY (sender_username) REFERENCES USERS(username));");
        stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_USER_LAST_SEEN + " ("
                + "username TEXT NOT NULL, group_id INTEGER NOT NULL, last_message_id INTEGER NOT NULL,"
                + "PRIMARY KEY (username, group_id),"
                + "FOREIGN KEY (username) REFERENCES USERS(username),"
                + "FOREIGN KEY (group_id) REFERENCES " + TABLE_GROUPS + "(group_id) ON DELETE CASCADE);");
        stmt.execute("INSERT OR IGNORE INTO " + TABLE_GROUPS + " (group_id, group_name) VALUES (" + ID_TODOS + ", '" + NOMBRE_TODOS + "')");
    }

    public static int obtenerGrupoIdPorNombre(String groupName) {
        String sql = "SELECT group_id FROM " + TABLE_GROUPS + " WHERE group_name = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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

        try (Connection conn = DatabaseManager.getConnection()) {
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
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        if (groupId == ID_TODOS) return miembros;
        String sql = "SELECT username FROM " + TABLE_GROUP_MEMBERS + " WHERE group_id = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = DatabaseManager.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) grupos.add(rs.getString("group_name"));
        } catch (SQLException e) {
            System.err.println("Error al obtener lista de grupos: " + e.getMessage());
        }
        return grupos;
    }

    public static long guardarMensajeGrupo(int gId, String sender, String content) {
        String sql = "INSERT INTO " + TABLE_GROUP_MESSAGES + " (group_id, sender_username, content) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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

    public static List<MensajeGrupo> obtenerMensajesNoVistos(String username, int groupId) {
        long lastSeenId = obtenerLastSeenId(username, groupId);
        return obtenerNuevosMensajesDesde(groupId, lastSeenId);
    }

    private static long obtenerLastSeenId(String username, int groupId) {
        String sql = "SELECT last_message_id FROM " + TABLE_USER_LAST_SEEN + " WHERE username = ? AND group_id = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setInt(2, groupId);
            pstmt.setLong(3, messageId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al actualizar último mensaje visto: " + e.getMessage());
        }
    }
}