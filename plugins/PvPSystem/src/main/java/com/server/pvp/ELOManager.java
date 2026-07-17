package com.server.pvp;

/**
 * Manages ELO rating calculations using the standard ELO formula.
 */
public class ELOManager {

    private int startingElo;
    private double kFactor;
    private int minimumElo;

    public ELOManager(int startingElo, double kFactor, int minimumElo) {
        this.startingElo = startingElo;
        this.kFactor = kFactor;
        this.minimumElo = minimumElo;
    }

    public int getStartingElo() {
        return startingElo;
    }

    public double getKFactor() {
        return kFactor;
    }

    public int getMinimumElo() {
        return minimumElo;
    }

    /**
     * Calculate the expected score (probability of winning) for player A against player B.
     * Uses the standard ELO formula: Ea = 1 / (1 + 10^((Rb - Ra) / 400))
     *
     * @param ratingA ELO rating of player A
     * @param ratingB ELO rating of player B
     * @return expected score for player A (0.0 to 1.0)
     */
    public double calculateExpectedScore(int ratingA, int ratingB) {
        return 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0));
    }

    /**
     * Calculate the new ELO rating for a player after a match.
     *
     * @param currentRating the player's current ELO rating
     * @param opponentRating the opponent's ELO rating
     * @param won true if the player won, false if they lost
     * @return the new ELO rating (clamped to minimumElo)
     */
    public int calculateNewRating(int currentRating, int opponentRating, boolean won) {
        double expectedScore = calculateExpectedScore(currentRating, opponentRating);
        double actualScore = won ? 1.0 : 0.0;
        int newRating = (int) Math.round(currentRating + kFactor * (actualScore - expectedScore));
        return Math.max(newRating, minimumElo);
    }

    /**
     * Calculate ELO changes for both players in a 1v1 match.
     *
     * @param winnerRating  winner's current ELO rating
     * @param loserRating   loser's current ELO rating
     * @return an array of [winnerNewRating, loserNewRating]
     */
    public int[] calculateMatchResult(int winnerRating, int loserRating) {
        int winnerNew = calculateNewRating(winnerRating, loserRating, true);
        int loserNew = calculateNewRating(loserRating, winnerRating, false);
        return new int[]{winnerNew, loserNew};
    }

    /**
     * Calculate the ELO difference (gain/loss) for a match.
     *
     * @param playerRating player's current ELO
     * @param opponentRating opponent's current ELO
     * @param won whether the player won
     * @return the ELO change (positive for gain, negative for loss)
     */
    public int getELOChange(int playerRating, int opponentRating, boolean won) {
        int newRating = calculateNewRating(playerRating, opponentRating, won);
        return newRating - playerRating;
    }

    /**
     * Get a rank title based on ELO rating.
     *
     * @param elo the ELO rating
     * @return the rank title string with color codes
     */
    public static String getEloRank(int elo) {
        if (elo >= 2000) return "§6§lChampion";
        if (elo >= 1800) return "§5§lMaster";
        if (elo >= 1600) return "§b§lDiamond";
        if (elo >= 1400) return "§e§lPlatinum";
        if (elo >= 1200) return "§7§lGold";
        if (elo >= 1000) return "§f§lSilver";
        return "§8§lBronze";
    }

    /**
     * Get a formatted ELO display string.
     *
     * @param elo the ELO rating
     * @return formatted string with rank and rating
     */
    public static String formatElo(int elo) {
        return getEloRank(elo) + " §r§7(" + elo + ")";
    }
}
