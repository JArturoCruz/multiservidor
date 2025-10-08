package servidormulti;

import java.io.*;
import java.util.HashMap;

public class BDusuarios {

    private static final String DB_FILE = "usuarios.txt";
    // Guarda los usuarios en memoria para acceso rápido: <nombreUsuario, pin>
    private static HashMap<String, String> usuarios = new HashMap<>();

    static {
        // Carga los usuarios del archivo al iniciar el servidor
        CargarUsuarios();
    }

    /**
     * Carga los usuarios del archivo a la memoria (HashMap).
     */
    private static void CargarUsuarios() {
        try (BufferedReader br = new BufferedReader(new FileReader(DB_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                // El archivo tiene el formato: usuario,pin
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    usuarios.put(parts[0].trim(), parts[1].trim());
                }
            }
            System.out.println("Base de datos de usuarios cargada. Total: " + usuarios.size());
        } catch (FileNotFoundException e) {
            System.out.println("Archivo de base de datos '" + DB_FILE + "' no encontrado. Se creará uno nuevo al registrar el primer usuario.");
        } catch (IOException e) {
            System.err.println("Error al leer la base de datos de usuarios: " + e.getMessage());
        }
    }

    /**
     * Guarda los usuarios de la memoria al archivo de texto.
     */
    private static void GuardarUsuarios() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(DB_FILE, false))) {
            for (String user : usuarios.keySet()) {
                pw.println(user + "," + usuarios.get(user));
            }
            System.out.println("Base de datos de usuarios guardada.");
        } catch (IOException e) {
            System.err.println("Error al guardar la base de datos de usuarios: " + e.getMessage());
        }
    }

    /**
     * Verifica si un usuario ya existe.
     */
    public static boolean UsuariosExistente(String usuario) {
        return usuarios.containsKey(usuario);
    }

    /**
     * Registra un nuevo usuario y guarda la base de datos.
     */
    public static boolean RegistrarUsuario(String usuario, String pin) {
        if (UsuariosExistente(usuario)) {
            return false; // Usuario ya existe
        }

        // La validación del PIN (4 dígitos) se hace en UnCliente antes de llamar aquí.

        usuarios.put(usuario, pin);
        GuardarUsuarios();
        return true;
    }

    /**
     * Verifica las credenciales de un usuario.
     */
    public static boolean AutenticarUsuario(String usuario, String pin) {
        if (UsuariosExistente(usuario)) {
            return usuarios.get(usuario).equals(pin);
        }
        return false; // El usuario no existe
    }
}