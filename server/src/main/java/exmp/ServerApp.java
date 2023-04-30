package exmp;

/**
 * Главный класс приложения. Запускает приложение, и обрабатывает ввод пользователя.
 */
public class ServerApp {
    /**
     * Точка входа в приложение.
     * Создает экземпляр класса App, читает ввод пользователя и передает его в метод readLine() этого объекта,
     * пока статус приложения активен.
     *
     * @param args аргументы командной строки, не используются
     */
    public static void main(String[] args) {
        exmp.Server server = new exmp.Server(38761, new exmp.App("F:\\Doc\\Projects\\ITMO-prog-lr6\\server\\src\\main\\resources\\input.xml"));
        server.start();
    }
}
