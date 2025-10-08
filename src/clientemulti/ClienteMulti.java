package clientemulti;

import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class ClienteMulti {

    public static void main(String[] args) throws IOException {
        Socket s = new Socket("localhost", 8080);

        System.out.print("Introduce tu nombre (será tu usuario): ");
        Scanner teclado = new Scanner(System.in);
        String nombre = teclado.nextLine();


        System.out.println("Tu nombre de usuario es: " + nombre);
        System.out.println("Como invitado, solo puedes enviar 3 mensajes.");
        System.out.println("Para enviar un mensaje privado, usa: @usuario mensaje");
        System.out.println("Para registrarte o iniciar sesión y enviar ilimitadamente, usa:");
        System.out.println("  - Registrar: /register <PIN de 4 dígitos> (Ej: /register 1234)");
        System.out.println("  - Iniciar Sesión: /login <PIN de 4 dígitos> (Ej: /login 1234)");


        Mandar paraMandar = new Mandar(s, nombre);
        Thread hiloParaMandar = new Thread(paraMandar);
        hiloParaMandar.start();

        Recibir paraRecibir = new Recibir(s);
        Thread hiloParaRecibir = new Thread(paraRecibir);
        hiloParaRecibir.start();
    }
}