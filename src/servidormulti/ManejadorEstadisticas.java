package servidormulti;

// --- IMPORTACIONES CORREGIDAS ---
// Asegúrate de que importa RJuego, NO BDusuarios
import bd.RJuego;
import bd.RJuego.RankingEntry;
// ---------------------------------

import java.io.IOException;
import java.util.List; // Importar List
import java.util.Map;

public class ManejadorEstadisticas {

    private final UnCliente cliente;
    private final FormateadorMensajes formateador;

    public ManejadorEstadisticas(UnCliente cliente) {
        this.cliente = cliente;
        this.formateador = new FormateadorMensajes();
    }

    public void manejar(String mensaje, String comando) throws IOException {
        if (comando.equals("/ranking")) manejarRanking();
        else if (comando.equals("/vs")) manejarVs(mensaje);
    }

    private void manejarRanking() throws IOException {
        // --- LÍNEA CORREGIDA ---
        // Ahora llama a RJuego.obtenerRankingGeneral()
        // Esto devuelve List<RJuego.RankingEntry>
        List<RankingEntry> ranking = RJuego.obtenerRankingGeneral();
        String respuesta = formateador.formatearRanking(ranking);
        cliente.enviarMensaje(respuesta);
    }

    private void manejarVs(String mensaje) throws IOException {
        String[] partes = mensaje.split(" ", 3);
        if (partes.length != 3) {
            cliente.enviarMensaje("Sistema VS: Uso incorrecto. Usa: /vs <usuario1> <usuario2>");
            return;
        }
        String user1 = partes[1].trim();
        String user2 = partes[2].trim();

        // Esta validación debe usar RUsuarios
        if (validarUsuariosVs(user1, user2)) {
            // Esta llamada usa RJuego
            Map<String, Integer> stats = RJuego.obtenerEstadisticasVs(user1, user2);
            String respuesta = formateador.formatearVs(stats, user1, user2);
            cliente.enviarMensaje(respuesta);
        }
    }

    private boolean validarUsuariosVs(String user1, String user2) throws IOException {
        if (user1.equalsIgnoreCase(user2)) {
            cliente.enviarMensaje("Sistema VS: Los usuarios deben ser diferentes.");
            return false;
        }
        // Validar existencia usa RUsuarios
        if (!bd.RUsuarios.UsuarioExistente(user1) || !bd.RUsuarios.UsuarioExistente(user2)) {
            cliente.enviarMensaje("Sistema VS: Asegúrate de que ambos usuarios existan.");
            return false;
        }
        return true;
    }
}