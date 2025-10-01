package clientemulti;
import java.io.IOException;
import java.net.Socket;
public class ClienteMulti {

    public static void main(String[] args) throws IOException {
        Socket s = new Socket("localhost",8080);
        Mandar paraMandar = new Mandar(s);
        Thread hiloParaMandar = new Thread(paraMandar);
        hiloParaMandar.start();

        Recibir Recibir = new Recibir(s);
        Thread hiloParaRecibir = new Thread(Recibir);
        hiloParaRecibir.start();
    }

}
