package com.example.ergasiaui.serializable;


import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import model.enums.BetCategory;

public class WorkerGame implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Game game;
    private float jackpot;
    private BetCategory betCategory;
    private final byte[] gameLogo;
    private int totalRating;
    private int ratingCount;
    private boolean enabled;
    private float totalProfits;    // total profits of THE GAME (not the players)
    private final Map<String, Float> playerProfits = new HashMap<>();  // list with player profits per player


    public WorkerGame(Game game, float jackpot, BetCategory betCategory, byte[] gameLogo) {
        this.game = game;
        this.jackpot = jackpot;
        this.betCategory = betCategory;
        this.gameLogo = gameLogo;
        this.totalRating = 0;
        this.ratingCount = 0;
        this.enabled = true;
        this.totalProfits = 0;
    }

    public byte[] getGameLogo() {
        return gameLogo;
    }

    public synchronized void recordBet(String playerId, float betAmount, float multiplier) {
        float playerWin = betAmount * multiplier;   // τι παίρνει πίσω ο παίκτης
        float systemProfit = betAmount - playerWin; // τι κερδίζει/χάνει το σύστημα

        totalProfits += systemProfit;

        float current = playerProfits.getOrDefault(playerId, 0.0f);
        playerProfits.put(playerId, current + (playerWin - betAmount));
    }

    public synchronized float getTotalProfits() {
        return totalProfits;
    }


    public synchronized boolean hasPlayer(String playerId) {
        return playerProfits.containsKey(playerId);
    }

    public synchronized float getPlayerProfits(String playerId) {
        return playerProfits.getOrDefault(playerId, 0.0f);
    }

    public synchronized void addRating(int rating) {
        totalRating += rating;
        ratingCount++;
    }

    public synchronized float getAverageRating() {
        if (ratingCount == 0) return 0;
        return (float) totalRating / ratingCount;
    }


    public Game getGame() {
        return game;
    }

    public double getJackpot() {
        return jackpot;
    }

    public BetCategory getBetCategory() {return betCategory;}

    public int getRatingCount() { return ratingCount; }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public synchronized void modifyJackpot(float jackpot) {this.jackpot = jackpot;}

    public synchronized void modifyBetCategory(BetCategory betCategory) {this.betCategory = betCategory;}

}
