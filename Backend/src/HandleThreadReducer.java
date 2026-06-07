import model.enums.ActionType;
import model.enums.Sender;
import serializables.Message;
import serializables.WorkerGame;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

public class HandleThreadReducer extends Thread {

    private final Socket client;
    private final ReduceTaskRepository reduceTaskRepository;
    private final Properties prop;

    public HandleThreadReducer(Socket client, ReduceTaskRepository reduceTaskRepository, Properties prop) {
        this.client = client;
        this.reduceTaskRepository = reduceTaskRepository;
        this.prop = prop;
    }

    @Override
    public void run() {
        try {
            // Stream Initialization
            ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(client.getInputStream());

            ReduceRequest messageFromWorker = (ReduceRequest) in.readObject();
            Object response = handleMessage(messageFromWorker);

            //out.writeObject(response);
            //out.flush();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String handleMessage(ReduceRequest messageFromWorker) {
        String requestId = messageFromWorker.getID();

        Collection<WorkerGame> workerGamesFromPayload = messageFromWorker.getResults();

        // Convert collection to list
        List<WorkerGame> workerGames = new ArrayList<>(workerGamesFromPayload);

        int expectedWorkers = Integer.parseInt(prop.getProperty("workers"));

        ReduceTask task = reduceTaskRepository.getOrCreateTask(requestId, expectedWorkers);

        task.addPartialResult(workerGames);

        if (task.isComplete()) {
            List<WorkerGame> finalResults = task.getReducedResult();

            sendReducedResultToMaster(requestId, finalResults);

            reduceTaskRepository.removeTask(requestId);
            System.out.println("Reduction completed");
            return "Reduction completed for requestId: " + requestId;
        }

        return "Partial result stored for requestId: " + requestId +
                " (" + task.getReceivedParts() + "/" + expectedWorkers + ")";
    }

    private void sendReducedResultToMaster(String requestId, List<WorkerGame> finalResults) {
        String masterIp = prop.getProperty("master.ip");
        int masterPort = Integer.parseInt(prop.getProperty("master.port"));

        try {
            Socket socketToMaster = new Socket(masterIp, masterPort);
            ObjectOutputStream out = new ObjectOutputStream(socketToMaster.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socketToMaster.getInputStream());

            // Send final results to Master
            Message<List<WorkerGame>> messageToMaster =
                    new Message<>(Sender.REDUCER, null, finalResults, requestId);

            out.writeObject(messageToMaster);
            out.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}