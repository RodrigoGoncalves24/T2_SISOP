import java.util.ArrayList;

public class GerenciadorMemoriaPaginado implements GerenciadorMemoria {

    private int tamPg;
    private int numFrames;
    private static ArrayList<Integer> framesAlocados;

    public GerenciadorMemoriaPaginado(int tamMem, int tamPg) {
        this.tamPg = tamPg;
        this.numFrames = tamMem / tamPg;
        framesAlocados = new ArrayList<>(numFrames);
        for (int i = 0; i < numFrames; i++) {
            framesAlocados.add(null);
        }
    }

    public GerenciadorMemoriaPaginado() {
    }

    @Override
    public ArrayList<Integer> aloca(int nroPalavras) {
        return null;
    }

    @Override
    public ArrayList<Integer> aloca(int frame, int tamPg, ArrayList<Integer> paginasUsadas) {
        ArrayList<Integer> framesAlocadosProPrograma = new ArrayList<>();

        for (Integer paginaUsada : paginasUsadas) {
            int initFrame = paginaUsada * tamPg;
            framesAlocadosProPrograma.add(initFrame);
        }

        return framesAlocadosProPrograma;
    }

    @Override
    public void traduzEndereco(int endereco, ArrayList<Integer> tabelaPaginas) {
        // Tradução feita diretamente pela CPU durante a execução.
    }

    @Override
    public void desaloca(ArrayList<Integer> tabelaPaginas) {
        if (tabelaPaginas == null) {
            return;
        }

        for (int frame : tabelaPaginas) {
            if (frame >= 0 && frame < numFrames) {
                framesAlocados.set(frame, null);
            }
        }
    }

    public void imprimeEstadoMemoria() {
        System.out.println("Estado dos frames:");
        for (int i = 0; i < numFrames; i++) {
            System.out.println("Frame " + i + ": " + (framesAlocados.get(i) == null ? "Livre" : "Ocupado"));
        }
    }

    public int getTamPg() {
        return tamPg;
    }
}
