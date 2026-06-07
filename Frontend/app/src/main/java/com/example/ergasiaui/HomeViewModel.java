package com.example.ergasiaui;

import androidx.lifecycle.ViewModel;

import java.util.List;

import model.enums.BetCategory;
import model.enums.RiskLevel;
import serializables.WorkerGame;

/**
 * HomeViewModel — διατηρεί τη λίστα παιχνιδιών και τα ενεργά φίλτρα
 * ανάμεσα σε HomeFragment ↔ FilterFragment.
 *
 * Το ViewModel ζει όσο ζει η Activity, οπότε τα φίλτρα
 * διατηρούνται όταν ο χρήστης γυρνάει πίσω από το FilterFragment.
 */
public class HomeViewModel extends ViewModel {

    // ── Game list ──────────────────────────────────────────────────────────
    private List<WorkerGame> games = null;

    public List<WorkerGame> getGames()                  { return games; }
    public void             setGames(List<WorkerGame> g){ games = g; }
    public boolean          hasGames()                  { return games != null && !games.isEmpty(); }

    // ── Active filters (null = no filter applied) ──────────────────────────
    private BetCategory activeBetCategory = null;
    private RiskLevel   activeRiskLevel   = null;
    private int         activeMinStars    = 0;       // 0 = no min

    public BetCategory getActiveBetCategory()              { return activeBetCategory; }
    public RiskLevel   getActiveRiskLevel()                { return activeRiskLevel; }
    public int         getActiveMinStars()                 { return activeMinStars; }

    public void setActiveFilters(BetCategory bet, RiskLevel risk, int stars) {
        this.activeBetCategory = bet;
        this.activeRiskLevel   = risk;
        this.activeMinStars    = stars;
    }

    /** true αν έχει εφαρμοστεί τουλάχιστον ένα φίλτρο */
    public boolean hasActiveFilters() {
        return activeBetCategory != null || activeRiskLevel != null || activeMinStars > 0;
    }

    /** Επαναφορά όλων των φίλτρων στην αρχική κατάσταση */
    public void clearFilters() {
        activeBetCategory = null;
        activeRiskLevel   = null;
        activeMinStars    = 0;
    }
}