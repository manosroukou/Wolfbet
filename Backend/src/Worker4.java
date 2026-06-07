import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Worker4 {

    private static final GameRepository localGames = new GameRepository();  // games that belong directly to the worker
    private static final Map<String, GameRepository> workerToGames = new HashMap<>();   // map of workers and their games as backup


    private static final float[] LOW_MULTIPLIERS = {
            0.0f, 0.0f, 0.0f, 0.1f, 0.5f, 1.0f, 1.1f, 1.3f, 2.0f, 2.5f
    };

    private static final float[] MEDIUM_MULTIPLIERS = {
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 2.5f, 3.5f
    };

    private static final float[] HIGH_MULTIPLIERS = {
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 2.0f, 6.5f
    };

    public static void main(String[] args) {

        Properties prop = new Properties();

        try {
            FileInputStream fis = new FileInputStream("src/config.properties");
            prop.load(fis);
        } catch (IOException e) {
            throw new RuntimeException("Could not load config.properties", e);
        }

        int workerPort = Integer.parseInt(prop.getProperty("worker4.port"));
        int healthPort = Integer.parseInt(prop.getProperty("worker4.healthport"));

        Thread healthListener = new Thread(() -> {
            try (ServerSocket healthSocket = new ServerSocket(healthPort)) {
                System.out.println("Health check listener started on port " + healthPort);
                while (true) {
                    Socket probe = healthSocket.accept();
                    probe.close(); // Just accept and close — that's enough to prove we're alive
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        healthListener.setDaemon(true);
        healthListener.start();

        try (ServerSocket serverSocket = new ServerSocket(workerPort)) {
            System.out.println("Worker 4 started at port " + workerPort);

            while (true) {
                Socket client = serverSocket.accept();
                new HandleThreadWorker(
                        client,
                        localGames,
                        workerToGames,
                        prop,
                        LOW_MULTIPLIERS,
                        MEDIUM_MULTIPLIERS,
                        HIGH_MULTIPLIERS
                ).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
