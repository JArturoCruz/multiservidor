package servidormulti;
import mensaje.Mensaje;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class UnCliente implements Runnable {

    final DataOutputStream salida;
    final DataInputStream entrada;
    private String nombreCliente;

    UnCliente(Socket s) throws IOException {
        salida = new DataOutputStream(s.getOutputStream());
        entrada = new DataInputStream(s.getInputStream());
    }

    public String getNombreCliente() {
        return nombreCliente;
    }

    public void enviarMensaje(String mensaje) throws IOException {
        this.salida.writeUTF(mensaje);
    }

    @Override
    public void run() {
        try {
            // 1. Recibir nombre y registrar cliente
            nombreCliente = entrada.readUTF();
            ServidorMulti.clientes.put(nombreCliente, this);
            Mensaje.notificarATodos(nombreCliente + " se ha unido al chat.", this);

            // 2. Bucle principal: escuchar y delegar
            while (true) {
                String mensaje = entrada.readUTF();
                Mensaje.procesar(mensaje, this); // Delega el trabajo
            }
        } catch (SocketException e) {
            if (nombreCliente != null) {
                ServidorMulti.clientes.remove(nombreCliente);
                Mensaje.notificarATodos(nombreCliente + " ha abandonado el chat.", null);
            }
        } catch (IOException ex) {
            System.out.println("Error de comunicación con " + (nombreCliente != null ? nombreCliente : "un cliente"));
        }
    }
}