/**
 * Driver de entrada do sistema operacional.
 *
 * Esta thread fica bloqueada no monitor do GeradorIO (nao ha polling). Quando o
 * dispositivo produz um numero, o driver guarda o evento em seu proprio buffer
 * e avisa o GerenciadorProcessos, que pode completar uma solicitacao ADDIO.
 */
public class VerificaIO extends Thread {
    private final GeradorIO geradorIO;
    private final Object monitor = new Object();

    private GerenciadorProcessos gerenciadorProcessos;
    private Integer valorLido;
    private boolean valorDisponivel;
    private volatile boolean executando = true;

    public VerificaIO(GeradorIO geradorIO) {
        super("VerificaIO");
        this.geradorIO = geradorIO;
        setDaemon(true);
    }

    public void setGerenciadorProcessos(GerenciadorProcessos gerenciadorProcessos) {
        this.gerenciadorProcessos = gerenciadorProcessos;
    }

    @Override
    public void run() {
        while (executando && !isInterrupted()) {
            try {
                int novoValor = geradorIO.aguardarEConsumirValor();

                synchronized (monitor) {
                    // Nao sobrescreve um evento se ainda nao existe processo bloqueado.
                    while (valorDisponivel && executando) {
                        monitor.wait();
                    }
                    if (!executando) {
                        return;
                    }
                    valorLido = novoValor;
                    valorDisponivel = true;
                }

                // O callback tenta acordar exatamente um processo bloqueado.
                if (gerenciadorProcessos != null) {
                    gerenciadorProcessos.verificaProcessosBloqueados();
                }
            } catch (InterruptedException e) {
                interrupt();
                return;
            }
        }
    }

    /** Retorna o ultimo valor capturado, sem consumi-lo. */
    public Integer getValorLido() {
        synchronized (monitor) {
            return valorLido;
        }
    }

    /**
     * Consome o evento de forma atomica. Retorna null quando ainda nao ha dado;
     * assim o gerenciador nunca precisa ficar consultando em um laco ocupado.
     */
    public Integer consumirValorLido() {
        synchronized (monitor) {
            if (!valorDisponivel) {
                return null;
            }
            int valor = valorLido;
            valorLido = null;
            valorDisponivel = false;
            monitor.notifyAll();
            return valor;
        }
    }

    public void encerrar() {
        executando = false;
        interrupt();
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }
}
