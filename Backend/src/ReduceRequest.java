import serializables.WorkerGame;

import java.io.Serializable;
import java.util.Collection;

public class ReduceRequest implements Serializable {
    private final Collection<WorkerGame> results;
    private final String ID;


    public ReduceRequest(Collection<WorkerGame> results, String ID) {
        this.results = results;
        this.ID = ID;
    }

    public Collection<WorkerGame> getResults() {
        return results;
    }

    public String getID() {
        return ID;
    }
}
