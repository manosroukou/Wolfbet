package serializables;

import model.enums.PlayGameResultType;

import java.io.Serializable;

public class PlayGameResult implements Serializable {
    private final PlayGameResultType resultType;
    private final float winAmount;


    public PlayGameResult(PlayGameResultType resultType, float winAmount) {
        this.resultType = resultType;
        this.winAmount = winAmount;
    }

    public PlayGameResultType getResultType() {
        return resultType;
    }

    public float getWinAmount() {
        return winAmount;
    }
}
