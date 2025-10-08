package servidormulti;

import java.io.*;
import java.util.HashMap;

public class BDusuarios {

    private static final String DB_FILE = "usuarios.txt";
    private static HashMap<String, String> usuarios = new HashMap<>();

    static {
        CargarUsuarios();
    }

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

    public static boolean UsuarioExistente(String usuario) {
        return usuarios.containsKey(usuario);
    }

    public static boolean RegistrarUsuario(String usuario, String pin) {
        if (UsuarioExistente(usuario)) {
            return false; // Usuario ya existe
        }


        usuarios.put(usuario, pin);
        GuardarUsuarios();
        return true;
    }

    public static boolean AutenticarUsuario(String usuario, String pin) {
        if (UsuarioExistente(usuario)) {
            return usuarios.get(usuario).equals(pin);
        }
        return false;
    }
}