package servidormulti;

import java.io.*;
import java.net.Socket;

public class UnCliente implements Runnable {

    final DataOutputStream salida;
    final DataInputStream entrada;
    private String nombre;

    UnCliente(Socket s) throws IOException {
        salida = new DataOutputStream(s.getOutputStream());
        entrada = new DataInputStream(s.getInputStream());
    }

    @Override
    public void run() {
        try {
            nombre = entrada.readUTF();
            System.out.println("Nuevo cliente conectado: " + nombre);

            String mensaje;
            while (true) {
                mensaje = entrada.readUTF();
                String mensajeConNombre = nombre + ": " + mensaje;
                for (UnCliente cliente : ServidorMulti.clientes.values()) {
                    cliente.salida.writeUTF(mensajeConNombre);
                }
            }
        } catch (IOException ex) {
            System.out.println(nombre + " se ha desconectado.");
        }
    }
}