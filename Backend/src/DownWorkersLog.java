import java.util.HashMap;
import java.util.Map;

public class DownWorkersLog {
    private final Map<String, Boolean> log = new HashMap<>();

    public synchronized void init(int noOfWorkers) {
        for (int i = 1; i <= noOfWorkers; i++) {
            log.put("worker" + i, true);
        }
    }

    public synchronized boolean isUp(String workerKey) {
        return log.getOrDefault(workerKey, false);
    }

    public synchronized void markDown(String workerKey) {
        log.put(workerKey, false);
    }

    public synchronized void markUp(String workerKey) {
        log.put(workerKey, true);
    }

    public synchronized Map<String, Boolean> snapshot() {
        return new HashMap<>(log);
    }

    public synchronized int size() {
        return log.size();
    }
}