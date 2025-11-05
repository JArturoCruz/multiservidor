package bd;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:chat_users.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void inicializarBaseDeDatos() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            RUsuarios.crearTablas(stmt);
            RJuego.crearTablas(stmt);
            RGrupos.crearTablas(stmt);
            System.out.println("Base de datos (SQLite) inicializada correctamente.");
        } catch (SQLException e) {
            System.err.println("Error al inicializar la base de datos SQLite: " + e.getMessage());
        }
    }
}