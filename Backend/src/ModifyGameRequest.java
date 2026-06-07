import model.enums.RiskLevel;
import java.io.Serializable;

public class ModifyGameRequest implements Serializable {
    private String gameName;
    private Float minBet;     // nullable (marked with uppercase F)
    private Float maxBet;     // nullable (marked with uppercase F)
    private RiskLevel risk;   // nullable

    public ModifyGameRequest() {}

    public ModifyGameRequest(String gameName, Float minBet, Float maxBet, RiskLevel risk) {
        this.gameName = gameName;
        this.minBet = minBet;
        this.maxBet = maxBet;
        this.risk = risk;
    }

    public String getGameName() {
        return gameName;
    }

    public Float getMinBet() {
        return minBet;
    }

    public Float getMaxBet() {
        return maxBet;
    }

    public RiskLevel getRiskLevel() {
        return risk;
    }

}
