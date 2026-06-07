public class PendingRequest {
    private final String requestId;
    private Object result;
    private boolean completed = false;

    public PendingRequest(String requestId) {
        this.requestId = requestId;
    }

    public synchronized void complete(Object result) {
        this.result = result;
        this.completed = true;
        notify();
    }

    public synchronized Object awaitResult() throws InterruptedException {
        while (!completed) {
            wait();
        }
        return result;
    }

    public String getRequestId() {
        return requestId;
    }
}