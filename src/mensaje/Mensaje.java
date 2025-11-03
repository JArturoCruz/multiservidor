package mensaje;
import servidormulti.ServidorMulti;
import servidormulti.UnCliente;
import bd.BDusuarios;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Mensaje {

    private static String formatearMensajePrivado(String remitenteNombre, String contenido) {
        return "(Privado de " + remitenteNombre + "): " + contenido;
    }

    private static String formatearConfirmacionPrivada(String destinatarios, String contenido) {
        return "(Mensaje privado para " + destinatarios + "): " + contenido;
    }

    public static boolean procesar(String mensaje, UnCliente remitente, ServidorMulti servidor) throws IOException {
        if (mensaje.startsWith("@")) {
            return enviarMensajePrivado(mensaje, remitente, servidor);
        } else {
            // Es un mensaje de grupo
            return difundirMensajeGrupo(mensaje, remitente, servidor);
        }
    }

    public static void enviarMensajePrivadoEntreJugadores(String mensaje, UnCliente remitente, String nombreDestino, ServidorMulti servidor) throws IOException {
        UnCliente clienteDestino = servidor.getCliente(nombreDestino);

        if (clienteDestino != null) {
            String mensajeParaDestinatarios = formatearMensajePrivado(remitente.getNombreCliente(), mensaje);
            clienteDestino.enviarMensaje(mensajeParaDestinatarios);

            String mensajeConfirmacion = formatearConfirmacionPrivada(nombreDestino, mensaje);
            remitente.enviarMensaje(mensajeConfirmacion);
        } else {
            remitente.enviarMensaje("Sistema: Error interno. El oponente (" + nombreDestino + ") no está conectado.");
        }
    }

    private static boolean validarMensajePrivado(String mensajePrivado, UnCliente remitente) throws IOException {
        if (mensajePrivado.isEmpty()) {
            remitente.enviarMensaje("Sistema: No puedes enviar un mensaje privado vacío.");
            return false;
        }
        return true;
    }

    private static void enviarAClientes(UnCliente remitente, String destinatariosStr, String mensajeParaDestinatarios, ServidorMulti servidor) throws IOException {
        String[] destinatarios = destinatariosStr.split(",");
        StringBuilder destinatariosEnviados = new StringBuilder();
        boolean alMenosUnoEnviado = false;

        for (String dest : destinatarios) {
            String nombreDestinatario = dest.trim();
            alMenosUnoEnviado = procesarDestinatarioPrivado(remitente, nombreDestinatario, mensajeParaDestinatarios, servidor, destinatariosEnviados) || alMenosUnoEnviado;
        }

        if (alMenosUnoEnviado) {
            String contenidoMensaje = mensajeParaDestinatarios.substring(mensajeParaDestinatarios.indexOf("): ") + 3);
            String mensajeConfirmacion = formatearConfirmacionPrivada(destinatariosEnviados.toString(), contenidoMensaje);
            remitente.enviarMensaje(mensajeConfirmacion);
        }
    }

    private static boolean procesarDestinatarioPrivado(UnCliente remitente, String nombreDestinatario, String mensajeParaDestinatarios, ServidorMulti servidor, StringBuilder destinatariosEnviados) throws IOException {
        UnCliente clienteDestino = servidor.getCliente(nombreDestinatario);
        String remitenteNombre = remitente.getNombreCliente();

        if (clienteDestino == null) {
            remitente.enviarMensaje("Sistema: El usuario '" + nombreDestinatario + "' no está conectado o no existe.");
            return false;
        }

        // Los invitados (anónimos) no pueden enviar ni recibir PMs, excepto si es de juego
        if (!remitente.isAutenticado() || !clienteDestino.isAutenticado()) {
            remitente.enviarMensaje("Sistema: Los mensajes privados solo están permitidos entre usuarios autenticados.");
            return false;
        }

        if (esBloqueoBidireccional(nombreDestinatario, remitenteNombre, remitente)) {
            return false;
        }

        clienteDestino.enviarMensaje(mensajeParaDestinatarios);
        if (destinatariosEnviados.length() > 0) destinatariosEnviados.append(", ");
        destinatariosEnviados.append(nombreDestinatario);
        return true;
    }

    private static boolean esBloqueoBidireccional(String nombre1, String nombre2, UnCliente notificador) throws IOException {
        // Los anónimos no pueden ser bloqueados
        if (nombre1.startsWith("anonimo") || nombre2.startsWith("anonimo")) return false;

        boolean bloqueadoPorDestino = BDusuarios.estaBloqueado(nombre1, nombre2);
        boolean bloqueadoPorRemitente = BDusuarios.estaBloqueado(nombre2, nombre1);

        if (bloqueadoPorDestino || bloqueadoPorRemitente) {
            String razon = bloqueadoPorDestino ? "El usuario te tiene bloqueado." : "Tienes bloqueado al usuario.";
            notificador.enviarMensaje("Sistema: Error al enviar mensaje privado a '" + nombre1 + "'. " + razon);
            return true;
        }
        return false;
    }

    private static boolean enviarMensajePrivado(String mensajeCompleto, UnCliente remitente, ServidorMulti servidor) throws IOException {
        // Los invitados no pueden enviar PM
        if (!remitente.isAutenticado()) {
            remitente.enviarMensaje("Sistema: Los invitados no pueden enviar mensajes privados. Regístrate.");
            return false;
        }

        String[] partes = mensajeCompleto.split(" ", 2);
        String destinatariosStr = partes[0].substring(1);
        String mensajePrivado = (partes.length > 1) ? partes[1] : "";

        if (!validarMensajePrivado(mensajePrivado, remitente)) return false;
        String mensajeParaDestinatarios = formatearMensajePrivado(remitente.getNombreCliente(), mensajePrivado);
        enviarAClientes(remitente, destinatariosStr, mensajeParaDestinatarios, servidor);
        return true;
    }

    // --- MÉTODO REESCRITO PARA GRUPOS ---
    private static boolean difundirMensajeGrupo(String mensaje, UnCliente remitente, ServidorMulti servidor) throws IOException {
        int groupId = remitente.getCurrentGroupId();
        String groupName = remitente.getCurrentGroupName();
        String senderName = remitente.getNombreCliente();

        // 1. Guardar mensaje en la BD
        long newMsgId = BDusuarios.guardarMensajeGrupo(groupId, senderName, mensaje);
        if (newMsgId == -1) {
            remitente.enviarMensaje("Sistema: Error interno al guardar el mensaje.");
            return false;
        }

        // 2. Formatear mensaje
        String msgFmt = "[" + groupName + "] " + senderName + ": " + mensaje;

        // 3. Actualizar el "visto" del remitente y enviarle confirmación
        BDusuarios.actualizarUltimoMensajeVisto(senderName, groupId, newMsgId);
        remitente.enviarMensaje("(Mensaje enviado a " + groupName + ")");

        // 4. Obtener todos los destinatarios online
        Collection<UnCliente> destinatariosOnline;
        if (groupId == BDusuarios.ID_TODOS) {
            // "Todos" incluye a todos los conectados (autenticados y anónimos)
            destinatariosOnline = servidor.getTodosLosClientes();
        } else {
            // Otros grupos solo incluyen miembros autenticados
            List<String> miembros = BDusuarios.obtenerMiembrosGrupo(groupId);
            destinatariosOnline = miembros.stream()
                    .map(servidor::getCliente)
                    .filter(Objects::nonNull) // Filtrar usuarios que no están online
                    .collect(Collectors.toList());
        }

        // 5. Enviar mensaje a los destinatarios
        for (UnCliente cliente : destinatariosOnline) {
            if (cliente == remitente) continue; // No reenviar al remitente

            // Solo enviar si el cliente está actualmente en ese grupo
            if (cliente.getCurrentGroupId() == groupId) {
                // Verificar bloqueo (los anónimos no pueden bloquear ni ser bloqueados)
                if (!BDusuarios.estaBloqueado(cliente.getNombreCliente(), senderName) &&
                        !BDusuarios.estaBloqueado(senderName, cliente.getNombreCliente())) {

                    cliente.enviarMensaje(msgFmt);
                    // Actualizar el "visto" del receptor
                    BDusuarios.actualizarUltimoMensajeVisto(cliente.getNombreCliente(), groupId, newMsgId);
                }
            }
            // Si el cliente está online pero en otro grupo,
            // recibirá el mensaje cuando haga '/join' a este grupo.
        }
        return true;
    }


    private static void iterarYNotificar(String notificacion, UnCliente clienteExcluido, ServidorMulti servidor) {
        // Las notificaciones del sistema (como unirse/salir) solo van al grupo "Todos"
        for (UnCliente cliente : servidor.getTodosLosClientes()) {
            try {
                if (cliente != clienteExcluido && cliente.getCurrentGroupId() == BDusuarios.ID_TODOS) {
                    cliente.enviarMensaje("Sistema: " + notificacion);
                }
            } catch (IOException e) {
                // Ignorar error al notificar
            }
        }
    }

    public static void notificarATodos(String notificacion, UnCliente clienteExcluido, ServidorMulti servidor) {
        System.out.println(notificacion);
        iterarYNotificar(notificacion, clienteExcluido, servidor);
    }
}