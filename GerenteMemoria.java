import java.util.ArrayList;

public class GerenteMemoria implements GerenciadorMemoria {
    private static int numFrames = 0;
    private static int tamPg;
    private static ArrayList<Boolean> paginasUsadas;

    public GerenteMemoria() {
    }

    public static void defineValores(int numFrames, int tamPg) {
        GerenteMemoria.numFrames = numFrames;
        GerenteMemoria.tamPg = tamPg;

        paginasUsadas = new ArrayList<>(numFrames);
        for (int i = 0; i < numFrames; i++) {
            paginasUsadas.add(Boolean.FALSE);
        }
    }

    @Override
    public ArrayList<Integer> aloca(int nroPalavrasASeremAlocadas) {
        ArrayList<Integer> paginasUsadasNoPrograma = new ArrayList<>();

        if (nroPalavrasASeremAlocadas <= 0) {
            return paginasUsadasNoPrograma;
        }

        int qtdPaginasNecessarias = (int) Math.ceil((double) nroPalavrasASeremAlocadas / tamPg);
        if (qtdPaginasNecessarias > numFrames) {
            return paginasUsadasNoPrograma;
        }

        for (int i = 0; i < numFrames && paginasUsadasNoPrograma.size() < qtdPaginasNecessarias; i++) {
            if (!paginasUsadas.get(i)) {
                paginasUsadasNoPrograma.add(i);
            }
        }

        if (paginasUsadasNoPrograma.size() < qtdPaginasNecessarias) {
            paginasUsadasNoPrograma.clear();
            return paginasUsadasNoPrograma;
        }

        for (int pagina : paginasUsadasNoPrograma) {
            paginasUsadas.set(pagina, true);
        }

        return paginasUsadasNoPrograma;
    }

    @Override
    public ArrayList<Integer> aloca(int frame, int tamPg, ArrayList<Integer> paginasUsadas) {
        return null;
    }

    @Override
    public void traduzEndereco(int endereco, ArrayList<Integer> tabelaPaginas) {
        int pagina = endereco / tamPg;

        if (pagina < 0 || pagina >= tabelaPaginas.size()) {
            throw new RuntimeException("Acesso invalido (posicao invalida).");
        }

        int offset = endereco % tamPg;
        int frame = tabelaPaginas.get(pagina);
        int enderecoFisico = frame * tamPg + offset;
        if (enderecoFisico < 0) {
            throw new RuntimeException("Endereco fisico invalido.");
        }
    }

    @Override
    public void desaloca(ArrayList<Integer> pagianasASeremDesalocadas) {
        if (pagianasASeremDesalocadas == null) {
            return;
        }

        for (int index : pagianasASeremDesalocadas) {
            if (index >= 0 && index < paginasUsadas.size()) {
                paginasUsadas.set(index, false);
            }
        }
    }
}
