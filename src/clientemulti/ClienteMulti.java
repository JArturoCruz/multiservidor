package clientemulti;

import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class ClienteMulti {

    public static void main(String[] args) throws IOException {
        Socket s = new Socket("localhost", 8080);

        System.out.println("\n Instrucciones");
        System.out.println("Actualmente eres un usuario anónimo y tienes 3 mensajes gratis.");
        System.out.println("Para enviar un mensaje privado, usa: @usuario mensaje");
        System.out.println("Para registrarte o iniciar sesión y establecer tu nombre, usa:");
        System.out.println("Registrar: /register <nombre_usuario> <PIN de 4 dígitos> (Ej: /register Arturo 1234)");
        System.out.println("Iniciar Sesión: /login <nombre_usuario> <PIN de 4 dígitos> (Ej: /login Arturo 1234)");


        Mandar paraMandar = new Mandar(s);
        Thread hiloParaMandar = new Thread(paraMandar);
        hiloParaMandar.start();

        Recibir paraRecibir = new Recibir(s);
        Thread hiloParaRecibir = new Thread(paraRecibir);
        hiloParaRecibir.start();
    }
}