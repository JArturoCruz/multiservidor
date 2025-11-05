package servidormulti;

import java.util.Objects;

public class Propuesta {
    private final String proponente; // Era UnCliente
    private final String invitado;   // Era UnCliente

    public Propuesta(String proponente, String invitado) {
        this.proponente = proponente;
        this.invitado = invitado;
    }

    public String getProponente() {
        return proponente;
    }

    public String getInvitado() {
        return invitado;
    }

    public String getOponente(String miNombre) {
        if (miNombre.equals(proponente)) {
            return invitado;
        } else if (miNombre.equals(invitado)) {
            return proponente;
        }
        return null; // No debería pasar si se usa correctamente
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Propuesta propuesta = (Propuesta) o;

        return (Objects.equals(proponente, propuesta.proponente) && Objects.equals(invitado, propuesta.invitado)) ||
                (Objects.equals(proponente, propuesta.invitado) && Objects.equals(invitado, propuesta.proponente));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(proponente) + Objects.hashCode(invitado);
    }
}