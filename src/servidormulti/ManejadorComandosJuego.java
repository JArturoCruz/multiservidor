package servidormulti;

import java.io.IOException;

public class ManejadorComandosJuego {

    private final ControladorJuego controladorJuego;
    private final GestorPropuestas gestorPropuestas;

    public ManejadorComandosJuego(ControladorJuego controladorJuego, GestorPropuestas gestorPropuestas) {
        this.controladorJuego = controladorJuego;
        this.gestorPropuestas = gestorPropuestas;
    }

    public void manejarComando(String mensaje, UnCliente remitente) throws IOException {
        if (!remitente.isAutenticado()) {
            remitente.enviarMensaje("Sistema Gato: Debes estar autenticado para jugar al Gato.");
            return;
        }

        String[] partes = mensaje.split(" ", 4);
        String comando = partes[0];
        String argumento1 = partes.length > 1 ? partes[1] : "";
        String argumento2 = partes.length > 2 ? partes[2] : "";
        String argumento3 = partes.length > 3 ? partes[3] : "";

        switch (comando) {
            case "/gato":
                gestorPropuestas.proponerJuego(remitente, argumento1);
                break;
            case "/accept":
                gestorPropuestas.aceptarPropuesta(remitente, argumento1);
                break;
            case "/reject":
                gestorPropuestas.rechazarPropuesta(remitente, argumento1);
                break;
            case "/move":
                controladorJuego.manejarMovimiento(remitente, argumento1, argumento2, argumento3);
                break;
            case "/si":
            case "/no":
                controladorJuego.manejarRespuestaRevancha(remitente, argumento1, comando.equals("/si"));
                break;
            default:
                remitente.enviarMensaje("Sistema Gato: Comando de juego desconocido.");
                break;
        }
    }
}