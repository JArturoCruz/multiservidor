package servidormulti;

public class Propuesta {
    private final UnCliente proponente;
    private final UnCliente invitado;

    public Propuesta(UnCliente proponente, UnCliente invitado) {
        this.proponente = proponente;
        this.invitado = invitado;
    }

    public UnCliente getProponente() {
        return proponente;
    }

    public UnCliente getInvitado() {
        return invitado;
    }
}