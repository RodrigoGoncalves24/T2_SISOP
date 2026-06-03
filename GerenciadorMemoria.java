import java.util.ArrayList;

public interface GerenciadorMemoria {

    // Retorna true e o vetor que em que página o frame será alocado
    ArrayList<Integer> aloca(int nroPalavras);

    // Para o gerenciador de memória paginado
    ArrayList<Integer> aloca(int frame, int tamPg, ArrayList<Integer> paginasUsadas);

    void traduzEndereco(int endereco, ArrayList<Integer> tabelaPaginas);

    // Libera frames alocados
    void desaloca(ArrayList<Integer> paginasLivres);


}
