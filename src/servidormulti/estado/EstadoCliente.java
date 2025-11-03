package servidormulti.estado;

import java.io.IOException;

public interface EstadoCliente {
    void procesarMensaje(String mensaje) throws IOException;
}