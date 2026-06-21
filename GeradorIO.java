import java.util.Random;

/**
 * Simula um dispositivo fisico de entrada.
 *
 * A classe nao conhece processos, CPU ou memoria. Sua unica responsabilidade e
 * produzir um evento de entrada a cada cinco segundos e entrega-lo para a
 * thread VerificaIO. O monitor abaixo implementa um buffer de uma posicao:
 * enquanto o valor anterior nao for capturado, o produtor aguarda. Dessa forma
 * nenhum evento e sobrescrito e um mesmo numero nunca pode ser lido duas vezes.
 */
public class GeradorIO extends Thread {

    private final Random random = new Random();
    private final Object monitor = new Object();

    private Integer valorGerado;
    private boolean valorDisponivel;
    private volatile boolean executando = true;

    public GeradorIO() {
        super("GeradorIO");
        // O dispositivo nao deve impedir o encerramento da JVM pelo terminal.
        setDaemon(true);
    }

    @Override
    public void run() {
        while (executando && !isInterrupted()) {
            try {
                Thread.sleep(20000);

                int novoValor = random.nextInt(0,100); // intervalo de valores gerados
                synchronized (monitor) {
                    // Buffer de uma posicao: evita substituir uma entrada ainda nao lida.
                    while (valorDisponivel && executando) {
                        monitor.wait();
                    }
                    if (!executando) {
                        return;
                    }

                    valorGerado = novoValor;
                    valorDisponivel = true;
                    /*
                     * Nao imprimimos aqui. Esta e uma thread de dispositivo e uma
                     * escrita concorrente no console poderia aparecer no meio do
                     * comando que o usuario esta digitando. O valor sera informado
                     * pelo driver somente quando atender de fato um ADDIO.
                     */
                    monitor.notifyAll();
                }
            } catch (InterruptedException e) {
                // A interrupcao e o mecanismo normal usado para encerrar o dispositivo.
                interrupt();
                return;
            }
        }
    }

    /**
     * Espera sem busy waiting ate o proximo evento do dispositivo.
     * Ao retornar, remove atomicamente o valor do buffer.
     */
    public int aguardarEConsumirValor() throws InterruptedException {
        synchronized (monitor) {
            while (!valorDisponivel && executando) {
                monitor.wait();
            }
            if (!executando) {
                throw new InterruptedException("Dispositivo de IO encerrado");
            }

            int valor = valorGerado;
            valorGerado = null;
            valorDisponivel = false;
            monitor.notifyAll(); // libera o produtor caso o buffer estivesse cheio
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
