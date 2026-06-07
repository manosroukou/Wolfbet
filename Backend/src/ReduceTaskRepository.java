import java.util.HashMap;
import java.util.Map;

public class ReduceTaskRepository {

    // requestId -> reduce task
    private final Map<String, ReduceTask> reduceTasks = new HashMap<>();

    public synchronized ReduceTask getOrCreateTask(String requestId, int expectedWorkers) {
        ReduceTask task = reduceTasks.get(requestId);

        if (task == null) {
            task = new ReduceTask(requestId, expectedWorkers);
            reduceTasks.put(requestId, task);
        }

        return task;
    }

    public synchronized ReduceTask getTask(String requestId) {
        return reduceTasks.get(requestId);
    }

    public synchronized void removeTask(String requestId) {
        reduceTasks.remove(requestId);
    }

    public synchronized boolean containsTask(String requestId) {
        return reduceTasks.containsKey(requestId);
    }
}