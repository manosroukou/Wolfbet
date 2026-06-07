import java.util.HashMap;
import java.util.Map;

public class GameSecretRepository {
    private final Map<String, String> secrets = new HashMap<>();

    public synchronized void addSecret(String gameName, String hashKey) {
        secrets.put(gameName, hashKey);
    }

    public synchronized String getSecret(String gameName) {
        return secrets.get(gameName);
    }

    public synchronized boolean containsSecret(String gameName) {
        return secrets.containsKey(gameName);
    }

    public synchronized void removeSecret(String gameName) {
        secrets.remove(gameName);
    }
}