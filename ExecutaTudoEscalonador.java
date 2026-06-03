public class ExecutaTudoEscalonador extends Thread {

    private final GerenciadorProcessos gp;

    public ExecutaTudoEscalonador(GerenciadorProcessos gp) {
        this.gp = gp;
    }

    @Override
    public void run() {
        gp.executaTodosEscalonados();
    }
}