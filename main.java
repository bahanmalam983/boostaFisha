import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * BoostaFisha — Azure Trench fishery simulation. All game logic, models, and I/O in one file.
 * Commissioned for EVM-aligned fishing ledger simulation. Cast lines, resolve species/weight, seasons, bait credits.
 */

// ============ Enums ============

enum FishSpecies {
    BASS(0, "Largemouth Bass", 800, 5200),
    TROUT(1, "Rainbow Trout", 400, 3800),
    PIKE(2, "Northern Pike", 1200, 7200),
    CARP(3, "Common Carp", 1500, 9500),
    PERCH(4, "Yellow Perch", 200, 2200),
    SALMON(5, "Atlantic Salmon", 2000, 5500),
    CATFISH(6, "Channel Catfish", 1000, 6800),
    TUNA(7, "Bluefin Tuna", 8000, 22000),
    COD(8, "Atlantic Cod", 1500, 4500),
    FLOUNDER(9, "Summer Flounder", 600, 3200),
    MACKEREL(10, "Spanish Mackerel", 400, 3800),
    SNAPPER(11, "Red Snapper", 800, 4200);

    private final int index;
    private final String displayName;
    private final int minGrams;
    private final int maxGrams;

    FishSpecies(int index, String displayName, int minGrams, int maxGrams) {
        this.index = index;
        this.displayName = displayName;
        this.minGrams = minGrams;
        this.maxGrams = maxGrams;
    }

    public int getIndex() { return index; }
    public String getDisplayName() { return displayName; }
    public int getMinGrams() { return minGrams; }
    public int getMaxGrams() { return maxGrams; }

    public static FishSpecies byIndex(int i) {
        for (FishSpecies s : values()) if (s.index == i) return s;
        return BASS;
    }

    public static final int SPECIES_COUNT = 12;
}

enum WeatherCondition {
    CLEAR(1.0),
    CLOUDY(0.95),
    LIGHT_RAIN(0.88),
    HEAVY_RAIN(0.72),
    FOG(0.85),
    WINDY(0.90),
    STORM(0.55);

    private final double catchMultiplier;

    WeatherCondition(double catchMultiplier) {
        this.catchMultiplier = catchMultiplier;
    }

    public double getCatchMultiplier() { return catchMultiplier; }

    public static WeatherCondition random() {
        return values()[ThreadLocalRandom.current().nextInt(values().length)];
    }
}

enum SeasonPhase {
    SPRING(0),
    SUMMER(1),
    AUTUMN(2),
    WINTER(3);

    private final int index;

    SeasonPhase(int index) { this.index = index; }

    public int getIndex() { return index; }

    public double speciesBonus(FishSpecies s) {
        int[][] bonus = {
            { 110, 95, 105, 100, 115, 90, 100, 85, 95, 100, 105, 100 },
            { 100, 105, 100, 110, 100, 105, 115, 120, 100, 115, 110, 115 },
            { 105, 110, 115, 105, 100, 115, 105, 95, 110, 95, 100, 105 },
            { 90, 85, 95, 90, 80, 85, 95, 75, 105, 90, 85, 90 }
        };
        return bonus[index][s.getIndex()] / 100.0;
    }

    public static SeasonPhase fromSeasonIndex(long seasonIndex) {
        return values()[(int) (seasonIndex % 4)];
    }
}

enum TackleType {
    BASIC(1.0, "Basic Rod"),
