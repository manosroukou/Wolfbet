import serializables.Game;

import java.io.Serializable;

public class CreateGameRequest implements Serializable {
    private final Game game;  
    private final byte[] gameLogoPNG;

    public CreateGameRequest(Game game, byte[] gameLogoPNG) {
        this.game = game;
        this.gameLogoPNG = gameLogoPNG;
    }

    public Game getGame() {
        return game;
    }

    public byte[] getGameLogoPNG() {
        return gameLogoPNG;
    }
}
