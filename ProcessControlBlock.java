import java.util.ArrayList;

public class ProcessControlBlock {
    public int id;
    public String nomePrograma;
    public Sistema.Word[] imagemPrograma;
    public ArrayList<Integer> tabelaPaginas;
    public String estado;
    public int pc; // program counter lógico
    public int[] registradores; // contexto da CPU salvo no PCB

    public ProcessControlBlock(int id, String nomePrograma, Sistema.Word[] imagemPrograma, ArrayList<Integer> paginasAlocadas, String pronto) {
        this.id = id;
        this.nomePrograma = nomePrograma;
        this.imagemPrograma = imagemPrograma;
        this.estado = pronto;
        this.pc = 0;
        this.tabelaPaginas = paginasAlocadas;
        this.registradores = new int[10];
    }
}
