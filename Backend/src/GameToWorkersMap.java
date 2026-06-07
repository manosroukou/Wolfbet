import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameToWorkersMap {
    private final Map<String, List<Integer>> map = new HashMap<>();

    public synchronized void put(String gameName, List<Integer> workers) {
        map.put(gameName, workers);
    }

    public synchronized List<Integer> get(String gameName) {
        return map.get(gameName);
    }

    public synchronized boolean containsGame(String gameName) {
        return map.containsKey(gameName);
    }
}