package serializables;

import java.io.Serializable;

public class PlayGameRequest implements Serializable {
    private final String playerId;
    private final String gameName;
    private final float betAmount;

    public PlayGameRequest(String playerId, String gameName, float betAmount) {
        this.playerId = playerId;
        this.gameName = gameName;
        this.betAmount = betAmount;
    }


    public String getPlayerId() {
        return playerId;
    }

    public String getGameName() {
        return gameName;
    }

    public float getBetAmount() {
        return betAmount;
    }
}
