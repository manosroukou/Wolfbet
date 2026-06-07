package serializables;

import model.enums.BetCategory;
import model.enums.RiskLevel;

import java.io.Serializable;

public class SearchFilters implements Serializable {

    private final Integer minStars;
    private final BetCategory betCategory;
    private final RiskLevel riskLevel;
    private final String ID;


    public SearchFilters(Integer minStars, BetCategory betCategory, RiskLevel riskLevel, String ID) {
        this.minStars = minStars;
        this.betCategory = betCategory;
        this.riskLevel = riskLevel;
        this.ID = ID;
    }

    public Integer getMinStars()   { return minStars; }
    public BetCategory getBetCategory() { return betCategory; }
    public RiskLevel getRiskLevel()   { return riskLevel; }
    public String getID() { return ID; }
}