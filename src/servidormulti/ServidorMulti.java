package servidormulti;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ServidorMulti {

    private final Map<String, UnCliente> clientes = new HashMap<>();
    private int anonimoCONT = 0;

    private final ControladorJuego controladorJuego;

    public ServidorMulti() {
        this.controladorJuego = new ControladorJuego(this);
    }

    public void agregarCliente(String nombre, UnCliente cliente) {
        synchronized (clientes) {
            clientes.put(nombre, cliente);
        }
    }

    public void removerCliente(String nombre) {
        UnCliente clienteRemovido;
        synchronized (clientes) {
            clienteRemovido = clientes.remove(nombre);
        }

        if (clienteRemovido != null) {
            try {
                controladorJuego.finalizarPorDesconexion(clienteRemovido);
            } catch (IOException e) {
            }
        }

        System.out.println("Cliente removido: " + nombre + ". Clientes conectados: " + clientes.size());
    }

    public UnCliente getCliente(String nombre) {
        return clientes.get(nombre);
    }

    public Collection<UnCliente> getTodosLosClientes() {
        return clientes.values();
    }

    public ControladorJuego getControladorJuego() {
        return controladorJuego;
    }

    public synchronized String generarNombreAnonimo() {
        anonimoCONT++;
        return "anonimo" + anonimoCONT;
    }

    public void iniciarServidor(int puerto) throws IOException {
        ServerSocket servidorSocket = new ServerSocket(puerto);
        System.out.println("Servidor iniciado y esperando clientes en el puerto " + puerto + "...");

        while (true) {
            Socket s = servidorSocket.accept();
            UnCliente unCliente = new UnCliente(s, this);
            Thread hilo = new Thread(unCliente);
            hilo.start();
        }
    }

    public static void main(String[] args) throws IOException {
        ServidorMulti servidor = new ServidorMulti();
        servidor.iniciarServidor(8080);
    }
}