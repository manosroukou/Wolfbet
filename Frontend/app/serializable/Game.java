package com.example.ergasiaui.serializable;

import java.io.Serializable;

import model.enums.RiskLevel;

public class Game implements Serializable {

    private static final long serialVersionUID = 1L;
    private String name;
    private String provider;
    private int stars;
    private int votes;
    private String gameLogo;
    private float minBet;
    private float maxBet;
    private RiskLevel riskLevel;
    private String hashKey;

    public Game(String name, String provider,
                int stars, int votes,
                String gameLogo, float minbet,
                float maxbet, RiskLevel riskLevel, String hashKey) {

        this.name = name;
        this.provider = provider;
        this.stars = stars;
        this.votes = votes;
        this.gameLogo = gameLogo;
        this.minBet = minbet;
        this.maxBet = maxbet;
        this.riskLevel = riskLevel;
        this.hashKey = hashKey;
    }

    public String getName() { return name; }
    public String getProvider() { return provider; }
    public int getStars() { return stars; }
    public int getVotes() { return votes; }
    public String getGameLogo() { return gameLogo; }
    public float getMinBet() { return minBet; }
    public float getMaxBet() { return maxBet; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public String getHashKey() { return hashKey; }

    public synchronized void modifyMinBet(float minBet) {
        this.minBet = minBet;
    }

    public synchronized void modifyMaxBet(float maxBet) {
        this.maxBet = maxBet;
    }

    public synchronized void modifyRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }



    @Override
    public String toString() {
        return "Game{" +
                "name='" + name + '\'' +
                ", provider='" + provider + '\'' +
                ", stars=" + stars +
                ", votes=" + votes +
                ", minbet=" + minBet +
                ", maxbet=" + maxBet +
                ", riskLevel=" + riskLevel +
                ", hashkey='" + hashKey + '\'' +
                '}';
    }
}