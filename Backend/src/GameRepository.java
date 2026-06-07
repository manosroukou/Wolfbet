import serializables.WorkerGame;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;

// Functions that access the shared 'games' field need to be locked(enter synchronously)
public class GameRepository implements Serializable {
    private final Map<String, WorkerGame> games = new HashMap<>();

    public synchronized void addWorkerGame(WorkerGame game) {
        games.put(game.getGame().getName(), game);
    }

    public synchronized WorkerGame getWorkerGame(String gameName) {
        return games.get(gameName);
    }

    public synchronized boolean containsGame(String gameName) {
        return games.containsKey(gameName);
    }

    public synchronized Collection<WorkerGame> getAll() {
        return new ArrayList<>(games.values());
    }

    public synchronized void replaceAll(GameRepository other) {
        games.clear();
        for (WorkerGame game : other.getAll()) {
            games.put(game.getGame().getName(), game);
        }
    }

}
