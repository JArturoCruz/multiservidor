package clientemulti;

import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class ClienteMulti {

    public static void main(String[] args) throws IOException {
        Socket s = new Socket("localhost", 8080);
        System.out.print("Ingresa tu nombre: ");
        Scanner scanner = new Scanner(System.in);
        String nombre = scanner.nextLine();

        Mandar paraMandar = new Mandar(s, nombre);
        Thread hiloParaMandar = new Thread(paraMandar);
        hiloParaMandar.start();

        Recibir paraRecibir = new Recibir(s);
        Thread hiloParaRecibir = new Thread(paraRecibir);
        hiloParaRecibir.start();
    }
}