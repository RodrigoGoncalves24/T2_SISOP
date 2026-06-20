import java.util.function.BooleanSupplier;

/**
 * Demonstracao solicitada para o trabalho:
 *
 * 1. varios processos disputam a CPU;
 * 2. o processo soma solicita IO e vai para BLOCKED;
 * 3. enquanto soma espera, dois processos fatorial usam a CPU e terminam;
 * 4. cada evento de IO devolve soma para READY;
 * 5. o escalonador volta a escolher soma ate ela terminar.
 *
 * Execute com:
 *
 *     javac *.java
 *     java TesteDemonstracaoEscalonamento
 */
public class TesteDemonstracaoEscalonamento {
    private static Sistema sistema;
    private static GerenciadorProcessos gerenciador;
    private static Thread threadEscalonador;

    public static void main(String[] args) throws Exception {
        try {
            iniciarSistemaSemTerminal();

            // A ordem e proposital: soma sera a primeira a receber a CPU e bloquear.
            ProcessControlBlock soma = criar(1, "soma");
            ProcessControlBlock fatorial1 = criar(2, "fatorial");
            ProcessControlBlock fatorial2 = criar(3, "fatorial");

            System.out.println();
            System.out.println("=== INICIO DA DEMONSTRACAO ===");
            System.out.println("READY inicial: soma, fatorial 1 e fatorial 2");
            gerenciador.executaTodosEscalonados();

            // O ADDIO avanca o PC antes de retirar soma da CPU. Portanto PC=2
            // comprova que o primeiro ADDIO foi executado e o contexto foi salvo.
            esperarAte(() -> estaBloqueadoNoPc(soma.id, 2), 2_000,
                    "soma nao bloqueou no primeiro ADDIO");
            System.out.println("[ETAPA 1 OK] soma saiu da CPU e esta BLOCKED no primeiro IO.");

            // O primeiro evento do dispositivo demora cinco segundos. Os fatoriais
            // precisam terminar nesse intervalo, usando a CPU liberada por soma.
            esperarAte(() -> gerenciador.obterProcesso(fatorial1.id) == null
                            && gerenciador.obterProcesso(fatorial2.id) == null,
                    4_000,
                    "os demais processos nao foram escalonados durante o IO");

            verificar(estaBloqueadoNoPc(soma.id, 2),
                    "soma deveria continuar bloqueada enquanto os fatoriais terminam");
            verificar(lerMemoriaLogica(fatorial1, 10) == 5040,
                    "primeiro fatorial produziu resultado incorreto");
            verificar(lerMemoriaLogica(fatorial2, 10) == 5040,
                    "segundo fatorial produziu resultado incorreto");
            System.out.println("[ETAPA 2 OK] enquanto soma estava BLOCKED, os dois fatoriais usaram a CPU e terminaram.");

            // Depois do primeiro evento, soma passa por READY, volta para a CPU,
            // configura r9=21, executa o segundo ADDIO e bloqueia com PC=4.
            esperarAte(() -> estaBloqueadoNoPc(soma.id, 4), 7_000,
                    "soma nao retornou a READY/CPU depois do primeiro IO");
            System.out.println("[ETAPA 3 OK] primeiro IO satisfeito: soma voltou a READY, foi escalonada e chegou ao segundo ADDIO.");

            // O segundo evento permite executar as instrucoes de soma e STOP.
            esperarAte(() -> gerenciador.obterProcesso(soma.id) == null, 7_000,
                    "soma nao terminou depois do segundo IO");

            int primeiroValor = lerMemoriaLogica(soma, 20);
            int segundoValor = lerMemoriaLogica(soma, 21);
            int resultado = lerMemoriaLogica(soma, 22);
            verificar(resultado == primeiroValor + segundoValor,
                    "resultado final da soma esta incorreto");
            System.out.println("[ETAPA 4 OK] segundo IO satisfeito: soma voltou a CPU e terminou: "
                    + primeiroValor + " + " + segundoValor + " = " + resultado);

            System.out.println("=== DEMONSTRACAO APROVADA ===");
        } finally {
            encerrarSistema();
        }
    }

    private static void iniciarSistemaSemTerminal() {
        sistema = new Sistema(1024, 8);
        Sistema.sistemaAtual = sistema;
        gerenciador = new GerenciadorProcessos(1024, 8, sistema);
        Sistema.gp = gerenciador;

        threadEscalonador = new Thread(() -> {
            while (sistema.sistemaOperacional.escalonadorAtivo) {
                gerenciador.passoEscalonadorContinuo();
            }
        }, "Escalonador-Demonstracao");
        threadEscalonador.start();
    }

    private static ProcessControlBlock criar(int id, String nomePrograma) {
        boolean criado = gerenciador.criaProcesso(
                nomePrograma,
                sistema.programas.retrieveProgram(nomePrograma));
        verificar(criado, "nao foi possivel criar " + nomePrograma);

        ProcessControlBlock pcb = gerenciador.obterProcesso(id);
        verificar(pcb != null, "PCB " + id + " nao foi encontrado");
        return pcb;
    }

    /** A consulta sincronizada garante que o teste enxergue o contexto salvo. */
    private static boolean estaBloqueadoNoPc(int id, int pcEsperado) {
        ProcessControlBlock pcb = gerenciador.obterProcesso(id);
        return pcb != null
                && "BLOQUEADO".equals(pcb.estado)
                && pcb.pc == pcEsperado;
    }

    private static int lerMemoriaLogica(ProcessControlBlock pcb, int enderecoLogico) {
        int pagina = enderecoLogico / Sistema.tamPg;
        int offset = enderecoLogico % Sistema.tamPg;
        int frame = pcb.tabelaPaginas.get(pagina);
        int enderecoFisico = frame * Sistema.tamPg + offset;
        return sistema.hardWare.memoria.posicao[enderecoFisico].parametro;
    }

    private static void esperarAte(BooleanSupplier condicao, long timeoutMs, String erro)
            throws Exception {
        long limite = System.currentTimeMillis() + timeoutMs;
        while (!condicao.getAsBoolean() && System.currentTimeMillis() < limite) {
            Thread.sleep(20);
        }
        verificar(condicao.getAsBoolean(), erro);
    }

    private static void verificar(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new AssertionError("TESTE FALHOU: " + mensagem);
        }
    }

    private static void encerrarSistema() throws InterruptedException {
        if (sistema != null) {
            sistema.sistemaOperacional.escalonadorAtivo = false;
        }
        if (gerenciador != null) {
            gerenciador.encerrarIO();
        }
        if (threadEscalonador != null) {
            threadEscalonador.interrupt();
            threadEscalonador.join(1_000);
        }
    }
}
