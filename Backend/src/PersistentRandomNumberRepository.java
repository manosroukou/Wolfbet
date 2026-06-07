import java.util.HashMap;
import java.util.Map;

// Used for active replication. When a back-up worker is replaying the game, it should grab
// the random number stored in the hashmap from the main/leader worker
// It searches for the number based on the requestId of the play action.

public class PersistentRandomNumberRepository {
    private final Map<String, PersistentRandomNumberWithBackUpCount> persistentRandomNumberRepository = new HashMap<>();

    public synchronized void addRandomNumberWithBackUpCount(String uuid, PersistentRandomNumberWithBackUpCount persistentItem) {
        persistentRandomNumberRepository.put(uuid, persistentItem);}

    public synchronized PersistentRandomNumberWithBackUpCount getRandomNumberWithBackUpCount(String uuid) {

        return persistentRandomNumberRepository.get(uuid);
    }

    public synchronized boolean containsRandomNumber(String uuid) {
        return persistentRandomNumberRepository.containsKey(uuid);
    }

    public synchronized void removePersistentRandomNumber(String uuid) {
        persistentRandomNumberRepository.remove(uuid);
    }
}
