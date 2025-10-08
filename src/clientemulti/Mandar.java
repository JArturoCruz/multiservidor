package clientemulti;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class Mandar implements Runnable {

    final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
    final DataOutputStream salida;
    private final String nombre;

    public Mandar(Socket s, String nombre) throws IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.nombre = nombre;
    }

    @Override
    public void run() {
        try {
            salida.writeUTF(nombre);
        } catch (IOException e) {
            e.printStackTrace();
        }

        while (true) {
            String mensaje;
            try {
                mensaje = teclado.readLine();

                salida.writeUTF(mensaje);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}