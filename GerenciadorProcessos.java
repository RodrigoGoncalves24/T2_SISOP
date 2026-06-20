import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GerenciadorProcessos extends Thread {

    private int proximoId = 1;
    private final List<ProcessControlBlock> filaProntos;
    private final List<ProcessControlBlock> filaBloqueados;
    private ProcessControlBlock processoRodando;
    private final Object lock = new Object();

    /*
     * Estar em READY nao significa que o usuario ja pediu a execucao. Este conjunto
     * separa as duas ideias: "new" cria um processo pronto, enquanto "exec" ou
     * "execAll" concede ao escalonador permissao para escolher aquele PCB.
     * O id permanece no conjunto durante um bloqueio de IO, permitindo a retomada.
     */
    private final Set<Integer> processosAutorizados = new HashSet<>();

    public static Map<Integer, ProcessControlBlock> listaProcessBlock = new HashMap<>();

    private final GerenteMemoria gm = new GerenteMemoria();
    private final Sistema sistema;

    // O dispositivo (produtor) e o driver (consumidor) possuem threads distintas.
    private final GeradorIO geradorIO = new GeradorIO();
    private final VerificaIO verificaIO = new VerificaIO(geradorIO);

    public GerenciadorProcessos(int tamMemoria, int tamPg, Sistema sistema) {
        this.sistema = sistema;
        this.filaProntos = sistema.sistemaOperacional.ready;
        this.filaBloqueados = sistema.sistemaOperacional.block;

        int numFrames = (int) Math.ceil((double) tamMemoria / tamPg);
        GerenteMemoria.defineValores(numFrames, tamPg);

        // A referencia permite que o driver sinalize a conclusao de uma entrada.
        verificaIO.setGerenciadorProcessos(this);
        geradorIO.start();
        verificaIO.start();
    }

    public boolean criaProcesso(String nomePrograma, Sistema.Word[] programa) {
        if (programa == null || programa.length == 0) {
            System.out.println("Programa invalido.");
            return false;
        }

        ArrayList<Integer> paginasAlocadas = gm.aloca(programa.length);
        if (paginasAlocadas == null || paginasAlocadas.isEmpty()) {
            System.out.println("Memoria insuficiente para criar o processo.");
            return false;
        }

        sistema.sistemaOperacional.utils.loadProgramPaged(programa, paginasAlocadas);

        synchronized (lock) {
            ProcessControlBlock pcb = new ProcessControlBlock(proximoId, nomePrograma, programa, paginasAlocadas, "PRONTO");
            listaProcessBlock.put(proximoId, pcb);
            filaProntos.add(pcb);
            System.out.println("Processo criado: " + pcb.id + " (" + nomePrograma + ")");
            proximoId++;
        }
        return true;
    }

    public void desaloca(int id) {
        synchronized (lock) {
            ProcessControlBlock pcb = listaProcessBlock.get(id);
            if (pcb == null) {
                System.out.println("Processo " + id + " nao encontrado.");
                return;
            }

            gm.desaloca(pcb.tabelaPaginas);
            filaProntos.remove(pcb);
            filaBloqueados.remove(pcb);

            if (processoRodando != null && processoRodando.id == id) {
                processoRodando = null;
                sistema.sistemaOperacional.running = null;
            }

            listaProcessBlock.remove(id);
            processosAutorizados.remove(id);
            lock.notifyAll();
            System.out.println("Processo removido: " + pcb.id);
        }
    }

    public void listarProcessos() {
        synchronized (lock) {
            if (listaProcessBlock.isEmpty()) {
                System.out.println("Sem processos no sistema.");
                return;
            }

            for (Map.Entry<Integer, ProcessControlBlock> entry : listaProcessBlock.entrySet()) {
                ProcessControlBlock pcb = entry.getValue();
                String fila = (processoRodando != null && processoRodando.id == pcb.id)
                        ? "RUNNING"
                        : (filaProntos.contains(pcb) ? "READY"
                        : (filaBloqueados.contains(pcb) ? "BLOCKED" : "OUTRA"));
                System.out.println("ID: " + pcb.id + " Programa: " + pcb.nomePrograma + " Estado: " + pcb.estado + " Fila: " + fila + " Paginas: " + pcb.tabelaPaginas);
                System.out.println("    PC: " + pcb.pc );
            }
        }
    }

    public void dumpProcesso(int id) {
        ProcessControlBlock pcb;
        synchronized (lock) {
            pcb = listaProcessBlock.get(id);
        }

        if (pcb == null) {
            System.out.println("Processo " + id + " nao encontrado.");
            return;
        }

        System.out.println("PCB => id=" + pcb.id + ", programa=" + pcb.nomePrograma + ", estado=" + pcb.estado + ", pc=" + pcb.pc + ", tabelaPaginas=" + pcb.tabelaPaginas);

        int totalPalavras = pcb.imagemPrograma.length;
        for (int i = 0; i < totalPalavras; i++) {
            int pagina = i / Sistema.tamPg;
            int offset = i % Sistema.tamPg;
            int frame = pcb.tabelaPaginas.get(pagina);
            int enderecoFisico = frame * Sistema.tamPg + offset;
            System.out.print(enderecoFisico + ":  ");
            sistema.sistemaOperacional.utils.dump(sistema.hardWare.memoria.posicao[enderecoFisico]);
        }
    }

    /** Autoriza um processo; a thread do escalonador executara as suas fatias. */
    public void executaProcesso(int id) {
        synchronized (lock) {
            ProcessControlBlock pcb = listaProcessBlock.get(id);
            if (pcb == null) {
                System.out.println("Processo " + id + " nao encontrado.");
                return;
            }
            processosAutorizados.add(id);
            lock.notifyAll(); // acorda o escalonador, que pode estar sem trabalho
            System.out.println("Processo " + id + " liberado para execucao.");
        }
    }

    /**
     * Consulta segura usada por diagnosticos e pelos testes de integracao.
     * A referencia retornada nao deve ser alterada por quem chama o metodo.
     */
    public ProcessControlBlock obterProcesso(int id) {
        synchronized (lock) {
            return listaProcessBlock.get(id);
        }
    }

    /** Autoriza todos os processos que existem no instante do comando execAll. */
    public void executaTodosEscalonados() {
        synchronized (lock) {
            if (listaProcessBlock.isEmpty()) {
                System.out.println("Sem processos para executar.");
                return;
            }
            processosAutorizados.addAll(listaProcessBlock.keySet());
            lock.notifyAll();
            System.out.println("Todos os processos existentes foram liberados para execucao.");
        }
    }

    /** Procura o primeiro READY cuja execucao foi solicitada pelo usuario. */
    private ProcessControlBlock proximoProcessoAutorizado() {
        for (ProcessControlBlock pcb : filaProntos) {
            if (processosAutorizados.contains(pcb.id)) {
                return pcb;
            }
        }
        return null;
    }

    /**
     * Executa uma fatia ou dorme ate existir trabalho autorizado. O wait elimina
     * tanto a execucao automatica apos "new" quanto o polling periodico da fila.
     */
    public void passoEscalonadorContinuo() {
        ProcessControlBlock pcb;
        synchronized (lock) {
            pcb = proximoProcessoAutorizado();
            while (sistema.sistemaOperacional.escalonadorAtivo
                    && (processoRodando != null || pcb == null)) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                pcb = proximoProcessoAutorizado();
            }

            if (!sistema.sistemaOperacional.escalonadorAtivo || pcb == null) {
                return;
            }
            filaProntos.remove(pcb);
        }

        executaFatia(pcb);
    }

    private void executaFatia(ProcessControlBlock pcb) {
        synchronized (lock) {
            processoRodando = pcb;
            sistema.sistemaOperacional.running = pcb;
            pcb.estado = "EXECUTANDO";
        }

        // Define context da CPU
        sistema.hardWare.cpu.setContext(pcb.pc, pcb.tabelaPaginas, pcb.registradores);

        // Executa o que foi definido
        sistema.hardWare.cpu.run(sistema.sistemaOperacional.delta, pcb.id);

        synchronized (lock) {
            //Verifica se o processo ainda existe, pois pode ter sido removido por interrupção
            if (!listaProcessBlock.containsKey(pcb.id)) {
                processoRodando = null;
                sistema.sistemaOperacional.running = null;
                return;
            }
            
            pcb.pc = sistema.hardWare.cpu.getPc();
            pcb.registradores = sistema.hardWare.cpu.getRegistradoresSnapshot();

            if (sistema.hardWare.cpu.parouPorStop()) {
                gm.desaloca(pcb.tabelaPaginas);
                listaProcessBlock.remove(pcb.id);
                processosAutorizados.remove(pcb.id);
                System.out.println("Processo finalizado e removido: " + pcb.id);
            } else if (sistema.hardWare.cpu.parouPorIO()) {
                // O ADDIO ja avancou o PC. O contexto salvo retomara exatamente na
                // instrucao seguinte quando o dispositivo concluir a operacao.
                pcb.estado = "BLOQUEADO";
                filaBloqueados.add(pcb);
                System.out.println("Processo " + pcb.id + " bloqueado aguardando IO.");
            } else {
                pcb.estado = "PRONTO";
                filaProntos.add(pcb);
            }

            processoRodando = null;
            sistema.sistemaOperacional.running = null;
            lock.notifyAll();
        }

        // Pode ja existir um evento no driver, gerado antes do ADDIO. A tentativa
        // e nao bloqueante e tambem elimina essa condicao de corrida.
        verificaProcessosBloqueados();
    }

    /**
     * Completa no maximo uma operacao de entrada pendente.
     *
     * O registrador 9 faz parte do contexto salvo no PCB e contem um endereco
     * LOGICO. Portanto a escrita precisa passar pela tabela de paginas do proprio
     * processo, e nao pode usar o numero diretamente como indice da memoria fisica.
     */
    public void verificaProcessosBloqueados() {
        synchronized (lock) {
            if (filaBloqueados.isEmpty()) {
                return;
            }

            Integer valor = verificaIO.consumirValorLido();
            if (valor == null) {
                return;
            }

            ProcessControlBlock pcb = filaBloqueados.get(0);
            int enderecoLogico = pcb.registradores[9];
            int pagina = enderecoLogico / Sistema.tamPg;
            int offset = enderecoLogico % Sistema.tamPg;

            if (enderecoLogico < 0 || pagina < 0 || pagina >= pcb.tabelaPaginas.size()) {
                // Nao acorda o processo como se a operacao tivesse dado certo.
                System.out.println("IO do processo " + pcb.id
                        + " falhou: endereco logico invalido em r9 (" + enderecoLogico + ").");
                return;
            }

            int frame = pcb.tabelaPaginas.get(pagina);
            int enderecoFisico = frame * Sistema.tamPg + offset;
            Sistema.Word destino = sistema.hardWare.memoria.posicao[enderecoFisico];
            destino.opcode = Sistema.Opcode.DATA;
            destino.registradorA = -1;
            destino.registradorB = -1;
            destino.parametro = valor;

            filaBloqueados.remove(0);
            pcb.estado = "PRONTO";
            filaProntos.add(pcb);
            System.out.println("[IO] Valor gerado " + valor + " gravado no endereco logico "
                    + enderecoLogico + " do processo " + pcb.id + ". Processo voltou para READY.");
            lock.notifyAll(); // acorda o escalonador para retomar o processo
        }
    }

    /** Encerra as duas threads de IO junto com o sistema. */
    public void encerrarIO() {
        verificaIO.encerrar();
        geradorIO.encerrar();
        synchronized (lock) {
            lock.notifyAll();
        }
    }

}
