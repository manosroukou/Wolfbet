import serializables.WorkerGame;

import java.io.Serializable;
import java.util.Collection;

public class ReducedResult implements Serializable {
    private final Collection<WorkerGame> finalGames;
    private final String requestId;

    public ReducedResult( Collection<WorkerGame> finalGames, String requestId) {
        this.finalGames = finalGames;
        this.requestId = requestId;
    }

    public Collection<WorkerGame> getFinalGames() {
        return finalGames;
    }

    public String getRequestId() {
        return requestId;
    }

}