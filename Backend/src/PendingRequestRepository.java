import java.util.HashMap;
import java.util.Map;

public class PendingRequestRepository {
    private final Map<String, PendingRequest> requests = new HashMap<>();

    public synchronized void add(PendingRequest request) {
        requests.put(request.getRequestId(), request);
    }

    public synchronized PendingRequest get(String requestId) {
        return requests.get(requestId);
    }

    public synchronized void remove(String requestId) {
        requests.remove(requestId);
    }

    public synchronized boolean contains(String requestId) {
        return requests.containsKey(requestId);
    }
}