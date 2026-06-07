import serializables.WorkerGame;

import java.util.ArrayList;
import java.util.List;

public class ReduceTask {
    private final String requestId;
    private final int expectedWorkers;
    private final List<WorkerGame> allResults = new ArrayList<>();
    private int receivedParts = 0;

    public ReduceTask(String requestId, int expectedWorkers) {
        this.requestId = requestId;
        this.expectedWorkers = expectedWorkers;
    }

    public synchronized void addPartialResult(List<WorkerGame> workerGames) {
        allResults.addAll(workerGames);
        receivedParts++;
    }

    public synchronized boolean isComplete() {
        return receivedParts == expectedWorkers;
    }

    public synchronized List<WorkerGame> getReducedResult() {
        return new ArrayList<>(allResults);
    }

    public synchronized int getReceivedParts() {
        return receivedParts;
    }

    public String getRequestId() {
        return requestId;
    }
}