public class Log {

    public static synchronized void registrar(String mensagem) {
        System.out.println(
                "[LOG][" + System.currentTimeMillis() + "] "
                        + mensagem
        );
    }
}