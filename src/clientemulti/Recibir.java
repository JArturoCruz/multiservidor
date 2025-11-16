package clientemulti;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class Recibir implements Runnable{
    final DataInputStream entrada;
    private final Socket socket;

    public Recibir(Socket s) throws IOException {
        this.socket = s;
        entrada = new DataInputStream(s.getInputStream());
    }

    @Override
    public void run() {
        String mensaje;
        mensaje = "";
        while(true){
            try {
                mensaje = entrada.readUTF();
                System.out.println(mensaje);
            } catch (IOException ex) {
                System.err.println("\n¡ADVERTENCIA DE CONEXIÓN!");
                System.err.println("Se ha perdido el contacto con el servidor. La comunicación se ha detenido.");

                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException closeEx) {

                }
                break;
            }
        }
    }
}