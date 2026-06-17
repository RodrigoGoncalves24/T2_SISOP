import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GerenciadorProcessos extends Thread {

    private int proximoId = 1;
    private final List<ProcessControlBlock> filaProntos;
    private final List<ProcessControlBlock> filaBloqueados;
    private ProcessControlBlock processoRodando;
    private final Object lock = new Object();

    public static Map<Integer, ProcessControlBlock> listaProcessBlock = new HashMap<>();

    private final GerenteMemoria gm = new GerenteMemoria();
    private final Sistema sistema;

    private final VerificaIO verificaIO = new VerificaIO();

    public GerenciadorProcessos(int tamMemoria, int tamPg, Sistema sistema) {
        this.sistema = sistema;
        this.filaProntos = sistema.sistemaOperacional.ready;
        this.filaBloqueados = sistema.sistemaOperacional.block;

        int numFrames = (int) Math.ceil((double) tamMemoria / tamPg);
        GerenteMemoria.defineValores(numFrames, tamPg);
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

            if (processoRodando != null && processoRodando.id == id) {
                processoRodando = null;
                sistema.sistemaOperacional.running = null;
            }

            listaProcessBlock.remove(id);
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
                        : (filaProntos.contains(pcb) ? "READY" : "OUTRA");
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

    public void executaProcesso(int id) {
        ProcessControlBlock pcb;
        synchronized (lock) {
            pcb = listaProcessBlock.get(id);
            if (pcb == null) {
                System.out.println("Processo " + id + " nao encontrado.");
                return;
            }

            if (!filaProntos.remove(pcb) && processoRodando != pcb) {
                System.out.println("Processo " + id + " nao esta apto para execucao.");
                return;
            }
        }

        // Executa o processo solicitado até terminar, em fatias delta.
        while (true) {
            executaFatia(pcb);
            synchronized (lock) {
                if (!listaProcessBlock.containsKey(id)) {
                    return;
                }
                filaProntos.remove(pcb);
            }
        }
    }

    public void executaTodosEscalonados() {
        System.out.println("Iniciando execução escalonada de todos os processos...");
        while(true) {
        ProcessControlBlock pcb;
        synchronized (lock) {
            if (filaProntos.isEmpty()) {
                System.out.println("Todos os processos finalizados.");
                break;
            }
            pcb = filaProntos.remove(0);    
        }
        executaFatia(pcb);
        }
    }

    public void passoEscalonadorContinuo() {
        ProcessControlBlock pcb;
        synchronized (lock) {
            if (!sistema.sistemaOperacional.escalonadorAtivo) {
                return;
            }
            if (processoRodando != null || filaProntos.isEmpty()) {
                return;
            }
            pcb = filaProntos.remove(0);
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
                System.out.println("Processo finalizado e removido: " + pcb.id);
            } else {
                pcb.estado = "PRONTO";
                filaProntos.add(pcb);
            }

            processoRodando = null;
            sistema.sistemaOperacional.running = null;
        }
    }

    // Função que bloqueia o processo e aguarda IO dele  
    public int funcaoQueBloqueiaProcessoEEsperaIO(int idProcesso){
        
        // Já removido antermente
        //filaProntos.remove(idProcesso);


        // Salva contexto
//        int [] registradoresSalvos = processoRodando.registradores;
//        int salvaPc = processoRodando.pc;
//        ArrayList<Integer> tabelaPaginasSalva = processoRodando.tabelaPaginas;
        
        // Adiciona na fila de bloqueados
       filaBloqueados.add(processoRodando);

    
        // Mandar a thread que verifica constantemente se há leitura  
        int valorLido = verificaIO.esperaEntradaESaida();

        // Ao retornar, precisa gerar exceção para a CPU e colocar o processo para pronto
        return  valorLido;
    }

}
