package bd;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class RUsuarios {

    private static final String TABLE_USERS = "USERS";
    private static final String TABLE_BLOCKS = "BLOCKS";

    public static void crearTablas(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_USERS + " (username TEXT PRIMARY KEY, pin TEXT NOT NULL);");
        stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_BLOCKS + " ("
                + "blocker_username TEXT NOT NULL, blocked_username TEXT NOT NULL,"
                + "PRIMARY KEY (blocker_username, blocked_username),"
                + "FOREIGN KEY (blocker_username) REFERENCES " + TABLE_USERS + "(username),"
                + "FOREIGN KEY (blocked_username) REFERENCES " + TABLE_USERS + "(username));");
    }

    public static boolean UsuarioExistente(String usuario) {
        String sql = "SELECT COUNT(*) FROM " + TABLE_USERS + " WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuario);
            pstmt.setString(2, pin);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error al registrar el usuario en SQLite: " + e.getMessage());
            return false;
        }
    }

    public static boolean AutenticarUsuario(String usuario, String pin) {
        String sql = "SELECT pin FROM " + TABLE_USERS + " WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
}