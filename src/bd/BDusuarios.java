package bd;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class BDusuarios {

    private static final String DB_URL = "jdbc:sqlite:chat_users.db";
    private static final String TABLE_USERS = "USERS";
    private static final String TABLE_BLOCKS = "BLOCKS";

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

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(sqlUsers);
            stmt.execute(sqlBlocks);
            System.out.println("Base de datos de usuarios (SQLite) inicializada. Tablas " + TABLE_USERS + " y " + TABLE_BLOCKS + " listas.");
        } catch (SQLException e) {
            System.err.println("Error al inicializar la base de datos SQLite: " + e.getMessage());
        }
    }

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
}