package clientemulti;

import java.io.IOException;
import java.net.Socket;

public class ClienteMulti {

    public static void main(String[] args) throws IOException {
        Socket s = new Socket("localhost", 8080);
        mostrarInstrucciones();

        Mandar paraMandar = new Mandar(s);
        Thread hiloParaMandar = new Thread(paraMandar);
        hiloParaMandar.start();

        Recibir paraRecibir = new Recibir(s);
        Thread hiloParaRecibir = new Thread(paraRecibir);
        hiloParaRecibir.start();
    }

    private static void mostrarInstrucciones() {
        System.out.println("\n--- BIENVENIDO AL CHAT ---");
        System.out.println("Eres un invitado. Solo puedes enviar 3 mensajes en el grupo 'Todos'.");
        System.out.println("\n--- COMANDOS BÁSICOS ---");
        System.out.println("Registrar: /register <nombre_usuario> <PIN de 4 dígitos>");
        System.out.println("Iniciar Sesión: /login <nombre_usuario> <PIN de 4 dígitos>");

        System.out.println("\n--- COMANDOS (AUTENTICADOS) ---");
        System.out.println("Mensaje Privado: @usuario <mensaje>");
        System.out.println("\n--- GRUPOS (AUTENTICADOS) ---");
        System.out.println("Ver Grupos: /glist");
        System.out.println("Unirse a Grupo: /join <nombre_grupo> (Ej: /join Todos)");
        System.out.println("Crear Grupo: /gcreate <nombre_grupo>");
        System.out.println("Eliminar Grupo: /gdelete <nombre_grupo>");

        System.out.println("\n--- JUEGO GATO (AUTENTICADOS) ---");
        System.out.println("Proponer: /gato <oponente>");
        System.out.println("Aceptar/Rechazar: /accept <oponente> | /reject <oponente>");
        System.out.println("Mover: /move <oponente> <fila> <columna>");
        System.out.println("Ranking: /ranking | Historial: /vs <user1> <user2>");
        System.out.println("----------------------------------\n");
    }
}