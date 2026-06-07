import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.io.FileInputStream;

public class Reducer {

    public static void main(String[] args) {

        ReduceTaskRepository reduceTaskRepository = new ReduceTaskRepository();
        Properties prop = new Properties();

        try {
            FileInputStream fis = new FileInputStream("src/config.properties");
            prop.load(fis);

        } catch (IOException e) {
            throw new RuntimeException("Could not load config.properties", e);
        }

        int reducerPort = Integer.parseInt(prop.getProperty("reducer.port"));

        try (ServerSocket serverSocket = new ServerSocket(reducerPort))
        {
            System.out.println("Reducer started at port " + reducerPort);

            while (true) {
                Socket client = serverSocket.accept();
                new HandleThreadReducer(client, reduceTaskRepository, prop).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}