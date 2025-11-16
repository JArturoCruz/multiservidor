package clientemulti;

import java.io.IOException;
import java.net.Socket;
import java.net.ConnectException;

public class ClienteMulti {

    private static final int TIEMPO_ESPERA_MS = 5000;

    public static void main(String[] args) {

        boolean conectado = false;

        while (!conectado) {
            try {
                System.out.println("Intentando conectar con el servidor (localhost:8080)...");
                Socket s = new Socket("localhost", 8080);
                conectado = true;
                System.out.println("¡Conexión exitosa!");

                mostrarInstrucciones();

                Mandar paraMandar = new Mandar(s);
                Thread hiloParaMandar = new Thread(paraMandar);
                hiloParaMandar.start();

                Recibir paraRecibir = new Recibir(s);
                Thread hiloParaRecibir = new Thread(paraRecibir);
                hiloParaRecibir.start();

                hiloParaRecibir.join();
                hiloParaMandar.join();

                System.err.println("\n--- El hilo de comunicación ha finalizado. Iniciando ciclo de RECONEXIÓN automática ---");
                conectado = false;

            } catch (ConnectException e) {
                System.err.println("❌ Error de conexión inicial: Servidor no disponible.");
                esperarParaReintento();

            } catch (IOException e) {
                System.err.println("❌ Error de comunicación. Asegúrese de que el servidor está en funcionamiento.");
                esperarParaReintento();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Programa interrumpido.");
                conectado = true;
            }
        }
    }

    private static void esperarParaReintento() {
        System.out.println("Esperando " + (TIEMPO_ESPERA_MS / 1000) + " segundos para reintentar...");
        try {
            Thread.sleep(TIEMPO_ESPERA_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static void mostrarInstrucciones() {
        System.out.println("\n--- BIENVENIDO AL CHAT ---");
        System.out.println("Eres un invitado. Solo puedes enviar 3 mensajes en el grupo 'Todos'.");
        System.out.println("\n--- COMANDOS BÁSICOS ---");
        System.out.println("Registrar: /register <nombre_usuario> <PIN de 4 dígitos>");
        System.out.println("Iniciar Sesión: /login <nombre_usuario> <PIN de 4 dígitos>");
        System.out.println("Cerrar Sesión: /logout"); // Nuevo

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