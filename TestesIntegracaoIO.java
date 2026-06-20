import java.util.function.BooleanSupplier;

/**
 * Testes de integracao do escalonador com o dispositivo de IO.
 *
 * Nao usa JUnit para que possa ser compilado diretamente com os demais arquivos:
 *
 *     javac *.java
 *     java TestesIntegracaoIO
 *
 * Como o dispositivo de producao gera um numero a cada cinco segundos, o teste
 * completo com duas somas leva aproximadamente vinte segundos (quatro entradas).
 */
public class TestesIntegracaoIO {
    private static Sistema sistema;
    private static GerenciadorProcessos gerenciador;
    private static Thread escalonador;

    public static void main(String[] args) throws Exception {
        try {
            prepararSistema();

            ProcessControlBlock soma1 = criarProcesso(1, "soma");
            ProcessControlBlock fatorial = criarProcesso(2, "fatorial");
            ProcessControlBlock soma2 = criarProcesso(3, "soma");

            testarNewNaoExecutaAutomaticamente(soma1, fatorial, soma2);
            testarExecLiberaSomenteProcessoEscolhido(soma1, fatorial, soma2);
            testarDuasOperacoesIOConcorrentes(soma1, soma2);

            System.out.println();
            System.out.println("========================================");
            System.out.println("TODOS OS TESTES DE INTEGRACAO PASSARAM");
            System.out.println("========================================");
        } finally {
            encerrarSistema();
        }
    }

    private static void prepararSistema() {
        sistema = new Sistema(1024, 8);
        Sistema.sistemaAtual = sistema;
        gerenciador = new GerenciadorProcessos(1024, 8, sistema);
        Sistema.gp = gerenciador;

        // Usa a mesma rotina do escalonador real, mas sem iniciar o terminal.
        escalonador = new Thread(() -> {
            while (sistema.sistemaOperacional.escalonadorAtivo) {
                gerenciador.passoEscalonadorContinuo();
            }
        }, "Escalonador-Teste");
        escalonador.start();
    }

    private static ProcessControlBlock criarProcesso(int idEsperado, String programa) {
        boolean criado = gerenciador.criaProcesso(
                programa,
                sistema.programas.retrieveProgram(programa));

        verificar(criado, "O programa " + programa + " deveria ter sido criado");
        ProcessControlBlock pcb = gerenciador.obterProcesso(idEsperado);
        verificar(pcb != null, "PCB " + idEsperado + " deveria existir");
        return pcb;
    }

    /** Garante que READY e EXECUTANDO continuam sendo estados diferentes. */
    private static void testarNewNaoExecutaAutomaticamente(ProcessControlBlock... processos)
            throws InterruptedException {
        Thread.sleep(1_000);

        for (ProcessControlBlock pcb : processos) {
            verificar(gerenciador.obterProcesso(pcb.id) != null,
                    "Processo " + pcb.id + " nao deveria finalizar sem exec");
            verificar(pcb.pc == 0,
                    "Processo " + pcb.id + " deveria continuar com PC 0 apos new");
            verificar("PRONTO".equals(pcb.estado),
                    "Processo " + pcb.id + " deveria permanecer PRONTO apos new");
        }
        passou("new apenas cria processos; nenhum foi executado automaticamente");
    }

    /**
     * Libera somente o fatorial e verifica tanto o isolamento dos outros PCBs
     * quanto o resultado 7! armazenado no endereco logico 10.
     */
    private static void testarExecLiberaSomenteProcessoEscolhido(
            ProcessControlBlock soma1,
            ProcessControlBlock fatorial,
            ProcessControlBlock soma2) throws Exception {

        gerenciador.executaProcesso(fatorial.id);
        esperarAte(() -> gerenciador.obterProcesso(fatorial.id) == null,
                5_000,
                "Fatorial nao terminou dentro do limite");

        verificar(soma1.pc == 0 && soma2.pc == 0,
                "exec 2 nao poderia executar os processos soma");
        verificar(lerMemoriaLogica(fatorial, 10) == 5040,
                "O resultado de 7! deveria ser 5040");
        passou("exec libera somente o ID escolhido e fatorial calculou 7! = 5040");
    }

    /**
     * Autoriza as duas somas simultaneamente. Ambas devem sair da CPU ao chegar
     * em ADDIO, aguardar quatro eventos independentes e finalizar corretamente.
     */
    private static void testarDuasOperacoesIOConcorrentes(
            ProcessControlBlock soma1,
            ProcessControlBlock soma2) throws Exception {

        gerenciador.executaTodosEscalonados();

        esperarAte(() -> {
                    // A consulta sincronizada tambem garante visibilidade das
                    // alteracoes de estado feitas pela thread do escalonador.
                    ProcessControlBlock atual1 = gerenciador.obterProcesso(soma1.id);
                    ProcessControlBlock atual2 = gerenciador.obterProcesso(soma2.id);
                    return atual1 != null && atual2 != null
                            && "BLOQUEADO".equals(atual1.estado)
                            && "BLOQUEADO".equals(atual2.estado);
                },
                2_000,
                "As duas somas deveriam bloquear no primeiro ADDIO");
        passou("dois processos soma puderam permanecer simultaneamente em BLOCKED");

        // Quatro entradas, uma a cada cinco segundos, mais uma margem para a CPU.
        esperarAte(() -> gerenciador.obterProcesso(soma1.id) == null
                        && gerenciador.obterProcesso(soma2.id) == null,
                27_000,
                "Os processos soma nao terminaram dentro do limite");

        validarResultadoSoma(soma1);
        validarResultadoSoma(soma2);
        passou("cada soma recebeu seus valores e preservou sua memoria paginada");
    }

    private static void validarResultadoSoma(ProcessControlBlock pcb) {
        int primeiro = lerMemoriaLogica(pcb, 20);
        int segundo = lerMemoriaLogica(pcb, 21);
        int resultado = lerMemoriaLogica(pcb, 22);

        verificar(primeiro >= 0 && primeiro <= 100,
                "Primeira entrada fora do intervalo no processo " + pcb.id);
        verificar(segundo >= 0 && segundo <= 100,
                "Segunda entrada fora do intervalo no processo " + pcb.id);
        verificar(resultado == primeiro + segundo,
                "Soma incorreta no processo " + pcb.id + ": "
                        + primeiro + " + " + segundo + " != " + resultado);
    }

    /** Traduz um endereco exatamente como a memoria paginada do processo. */
    private static int lerMemoriaLogica(ProcessControlBlock pcb, int enderecoLogico) {
        int pagina = enderecoLogico / Sistema.tamPg;
        int offset = enderecoLogico % Sistema.tamPg;
        int frame = pcb.tabelaPaginas.get(pagina);
        int enderecoFisico = frame * Sistema.tamPg + offset;
        return sistema.hardWare.memoria.posicao[enderecoFisico].parametro;
    }

    /** Espera com timeout para que uma falha nunca deixe o teste travado. */
    private static void esperarAte(
            BooleanSupplier condicao,
            long timeoutMs,
            String mensagemErro) throws Exception {

        long limite = System.currentTimeMillis() + timeoutMs;
        while (!condicao.getAsBoolean() && System.currentTimeMillis() < limite) {
            Thread.sleep(20);
        }
        verificar(condicao.getAsBoolean(), mensagemErro);
    }

    private static void verificar(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new AssertionError("FALHOU: " + mensagem);
        }
    }

    private static void passou(String descricao) {
        System.out.println("[PASSOU] " + descricao);
    }

    private static void encerrarSistema() throws InterruptedException {
        if (sistema != null) {
            sistema.sistemaOperacional.escalonadorAtivo = false;
        }
        if (gerenciador != null) {
            gerenciador.encerrarIO();
        }
        if (escalonador != null) {
            escalonador.interrupt();
            escalonador.join(1_000);
        }
    }
}
