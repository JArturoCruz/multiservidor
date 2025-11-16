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
                System.err.println("\n*** ERROR DE CONEXIÓN: Se ha perdido la conexión con el servidor. ***");
                System.err.println("Volverá a ser un invitado. Por favor, reinicie la aplicación para volver a intentar.");

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