package servidormulti;

import bd.BDusuarios;
import bd.BDusuarios.RankingEntry;

import java.util.List;
import java.util.Map;

public class FormateadorMensajes {

    public String[] formatearBienvenida() {
        return new String[]{
                "Sistema: Bienvenido. Actualmente estás en el grupo: " + BDusuarios.NOMBRE_TODOS,
                "Sistema: Como invitado, solo puedes enviar " + UnCliente.LIMITE_MENSAJES_GRATIS + " mensajes en el grupo 'Todos'.",
                "Sistema: Usa '/register <nombre_usuario> <PIN>' o '/login <nombre_usuario> <PIN>'.",
                "Sistema: Los usuarios autenticados pueden usar:",
                "Sistema: - '/glist' (Ver todos los grupos)",
                "Sistema: - '/gcreate <nombre_grupo>' (Crear un grupo)",
                "Sistema: - '/gdelete <nombre_grupo>' (Borrar un grupo)",
                "Sistema: - '/join <nombre_grupo>' (Unirse y cambiar a un grupo)",
                "Sistema: - '/block <usuario>' y '/unblock <usuario>'",
                "Sistema: Juega al Gato (comandos /gato, /accept, /move, /ranking, /vs, etc.)",
                "Sistema: REGLA: Solo puedes jugar al Gato con usuarios en tu mismo grupo actual."
        };
    }

    public String formatearListaGrupos(List<String> grupos) {
        if (grupos.isEmpty()) {
            return "Sistema: No hay grupos disponibles.";
        }
        StringBuilder sb = new StringBuilder("--- GRUPOS DISPONIBLES ---\n");
        for (String grupo : grupos) {
            sb.append("- ").append(grupo).append("\n");
        }
        sb.append("----------------------------");
        return sb.toString();
    }

    public String formatearRanking(List<RankingEntry> ranking) {
        if (ranking.isEmpty()) {
            return "Sistema Ranking: No hay datos de ranking disponibles.";
        }
        StringBuilder sb = new StringBuilder("\n--- RANKING GATO ---\n");
        sb.append(String.format("%-4s %-15s %-7s %-7s %-7s %s\n", "POS", "USUARIO", "PTS", "V", "E", "D"));
        sb.append("---------------------------------------------------\n");

        int limite = Math.min(ranking.size(), 10);
        for (int i = 0; i < limite; i++) {
            RankingEntry entry = ranking.get(i);
            sb.append(String.format("%-4d %-15s %-7d %-7d %-7d %d\n",
                    i + 1, entry.username, entry.points,
                    entry.victories, entry.ties, entry.defeats));
        }
        sb.append("---------------------------------------------------\n");
        return sb.toString();
    }

    public String formatearVs(Map<String, Integer> stats, String user1, String user2) {
        int total = stats.get("total");
        if (total == 0) {
            return "Sistema VS: Nunca han jugado " + user1 + " contra " + user2 + ".";
        }

        int user1Wins = stats.get(user1 + "_wins");
        int user2Wins = stats.get(user2 + "_wins");
        int ties = stats.get("ties");

        double user1WinRate = (double) user1Wins / total * 100;
        double user2WinRate = (double) user2Wins / total * 100;

        StringBuilder sb = new StringBuilder("\n--- ESTADÍSTICAS VS: " + user1 + " vs " + user2 + " ---\n");
        sb.append(String.format("Total Partidas: %d\n", total));
        sb.append("---------------------------------------------------\n");
        sb.append(String.format("%-15s | %-15s\n", user1, user2));
        sb.append("---------------------------------------------------\n");
        sb.append(String.format("%-15s | %-15s\n", user1Wins + " Victorias", user2Wins + " Victorias"));
        sb.append(String.format("%-15s | %-15s\n", String.format("%.2f%%", user1WinRate), String.format("%.2f%%", user2WinRate)));
        sb.append(String.format("Empates: %d\n", ties));
        sb.append("---------------------------------------------------\n");
        return sb.toString();
    }
}