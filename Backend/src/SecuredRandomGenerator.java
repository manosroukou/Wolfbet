import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SecuredRandomGenerator {

    // A hashMap containing <String, Buffer>, where String == gameName and Buffer == its unique buffer
    private static final RandomBufferRepository randomGamesBuffer = new RandomBufferRepository();

    // A hashMap containing <String, hashKey>, where String == gameName and hashKey == its unique hashKey
    private static final GameSecretRepository secrets = new GameSecretRepository();

    private static final PersistentRandomNumberRepository persistentRandomNumbers = new PersistentRandomNumberRepository();

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(8000);
            System.out.println("SRG up and running...");
            while(true) {
                Socket client = serverSocket.accept();
                new HandleThreadSRG(client, randomGamesBuffer, secrets, persistentRandomNumbers).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
