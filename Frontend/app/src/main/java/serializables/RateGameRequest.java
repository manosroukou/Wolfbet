package serializables;

import java.io.Serializable;

public class RateGameRequest implements Serializable {
    private final String gameName;
    private final int rating;

    public RateGameRequest(String gameName, int rating) {
        this.gameName = gameName;
        this.rating = rating;
    }

    public String getGameName() { return gameName; }

    public int getRating() { return rating; }
}