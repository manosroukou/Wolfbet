import java.util.Map;
import java.util.HashMap;
import java.util.Collection;

public class RandomBufferRepository {

    private final Map<String, RandomNumberBuffer> buffers = new HashMap<>();

    // Add buffer when a new game is registered
    public synchronized void addBuffer(String gameId, RandomNumberBuffer buffer) {
        buffers.put(gameId, buffer);
    }

    // Get buffer for a game
    public synchronized RandomNumberBuffer getBuffer(String gameId) {
        return buffers.get(gameId);
    }

    // Check if buffer exists
    public synchronized boolean containsBuffer(String gameId) {
        return buffers.containsKey(gameId);
    }

    // Get all buffers (for our debugging)
    public synchronized Collection<RandomNumberBuffer> getAll() {
        return buffers.values();
    }
}