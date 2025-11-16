package clientemulti;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class Mandar implements Runnable {

    final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
    final DataOutputStream salida;
    private final Socket socket;

    public Mandar(Socket s) throws IOException {
        this.socket = s;
        this.salida = new DataOutputStream(s.getOutputStream());
    }

    @Override
    public void run() {

        while (true) {
            String mensaje;
            try {
                if (socket.isClosed()) {
                    break;
                }

                mensaje = teclado.readLine();

                salida.writeUTF(mensaje);
            } catch (IOException e) {

                if (!socket.isClosed()) {
                    System.err.println("\n*** ERROR DE CONEXIÓN: Fallo al enviar mensaje. Cerrando conexión. ***");
                    try {
                        socket.close();
                    } catch (IOException closeEx) {

                    }
                }
                break;
            }
        }
    }
}