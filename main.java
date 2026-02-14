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
    SPINNING(1.15, "Spinning Rod"),
    BAITCAST(1.25, "Baitcast Rod"),
    FLY(1.20, "Fly Rod"),
    ICE(0.95, "Ice Rod"),
    SURF(1.30, "Surf Rod");

    private final double weightBonus;
    private final String label;

    TackleType(double weightBonus, String label) {
        this.weightBonus = weightBonus;
        this.label = label;
    }

    public double getWeightBonus() { return weightBonus; }
    public String getLabel() { return label; }
}

// ============ Fish model ============

final class Fish {
    private final FishSpecies species;
    private final int weightGrams;
    private final double rarityFactor;

    public Fish(FishSpecies species, int weightGrams, double rarityFactor) {
        this.species = species;
        this.weightGrams = Math.max(species.getMinGrams(), Math.min(species.getMaxGrams(), weightGrams));
        this.rarityFactor = Math.max(0.5, Math.min(1.5, rarityFactor));
    }

    public FishSpecies getSpecies() { return species; }
    public int getWeightGrams() { return weightGrams; }
    public double getRarityFactor() { return rarityFactor; }

    public int baitCredits() {
        int base = 75;
        double w = (double) weightGrams / (species.getMaxGrams());
        return (int) (base * w * rarityFactor);
    }

    @Override
    public String toString() {
        return String.format("%s %,d g (rarity %.2f)", species.getDisplayName(), weightGrams, rarityFactor);
    }
}

// ============ Catch record ============

final class CatchRecord {
    private final long blockNumber;
    private final String slotId;
    private final Fish fish;
    private final int baitCredits;
    private final WeatherCondition weather;
    private final TackleType tackle;

    public CatchRecord(long blockNumber, String slotId, Fish fish, int baitCredits,
                       WeatherCondition weather, TackleType tackle) {
        this.blockNumber = blockNumber;
        this.slotId = slotId;
        this.fish = fish;
        this.baitCredits = baitCredits;
        this.weather = weather;
        this.tackle = tackle;
    }

    public long getBlockNumber() { return blockNumber; }
    public String getSlotId() { return slotId; }
    public Fish getFish() { return fish; }
    public int getBaitCredits() { return baitCredits; }
    public WeatherCondition getWeather() { return weather; }
    public TackleType getTackle() { return tackle; }
}

// ============ Angler (player) ============

final class Angler {
    private final String address;
    private int baitBalance;
    private long lastCastBlock;
    private long lastSeasonIndex;
    private int claimedThisSeason;
    private final List<CatchRecord> catchHistory;

    public static final int MAX_CLAIM_PER_SEASON = 750;
    public static final int CAST_COOLDOWN_BLOCKS = 48;
    public static final int BAIT_PER_CATCH = 75;

    public Angler(String address) {
        this.address = address;
        this.baitBalance = 0;
        this.lastCastBlock = 0;
        this.lastSeasonIndex = -1;
        this.claimedThisSeason = 0;
        this.catchHistory = new ArrayList<>();
    }

    public String getAddress() { return address; }
    public int getBaitBalance() { return baitBalance; }
    public long getLastCastBlock() { return lastCastBlock; }
    public long getLastSeasonIndex() { return lastSeasonIndex; }
    public int getClaimedThisSeason() { return claimedThisSeason; }
    public List<CatchRecord> getCatchHistory() { return Collections.unmodifiableList(catchHistory); }

    public void creditBait(int amount) {
        this.baitBalance += amount;
    }

    public void setLastCastBlock(long block) {
        this.lastCastBlock = block;
    }

    public void advanceSeason(long newSeasonIndex) {
        if (newSeasonIndex > lastSeasonIndex) {
            lastSeasonIndex = newSeasonIndex;
            claimedThisSeason = 0;
        }
    }

    public void addClaimedThisSeason(int amount) {
        this.claimedThisSeason += amount;
    }

    public void recordCatch(CatchRecord record) {
        catchHistory.add(record);
    }

    public boolean canCast(long currentBlock, long currentSeason) {
        advanceSeason(currentSeason);
        if (currentBlock < lastCastBlock + CAST_COOLDOWN_BLOCKS) return false;
        return claimedThisSeason + BAIT_PER_CATCH <= MAX_CLAIM_PER_SEASON;
    }
}

// ============ Catch slot (lake spot) ============

final class CatchSlot {
    private final String slotId;
    private final FishSpecies species;
    private final int maxWeightGrams;
    private final long enlistedAtBlock;
    private final boolean filled;

    public CatchSlot(String slotId, FishSpecies species, int maxWeightGrams, long enlistedAtBlock, boolean filled) {
        this.slotId = slotId;
        this.species = species;
        this.maxWeightGrams = maxWeightGrams;
        this.enlistedAtBlock = enlistedAtBlock;
        this.filled = filled;
    }

    public String getSlotId() { return slotId; }
    public FishSpecies getSpecies() { return species; }
    public int getMaxWeightGrams() { return maxWeightGrams; }
    public long getEnlistedAtBlock() { return enlistedAtBlock; }
    public boolean isFilled() { return filled; }
}

// ============ Lake (fishing venue) ============

final class Lake {
    private static final int MAX_SLOTS = 96;
    private final Map<String, CatchSlot> slots = new HashMap<>();
    private final List<String> slotIdList = new ArrayList<>();
    private final byte[] catchSeed;

    public Lake(byte[] catchSeed) {
        this.catchSeed = catchSeed != null ? catchSeed : new byte[] { 0x5e, 0x4d, 0x3c, 0x2b };
    }

    public int getSlotCount() { return slotIdList.size(); }

    public String getSlotIdAt(int index) {
        if (index < 0 || index >= slotIdList.size()) return null;
        return slotIdList.get(index);
    }

    public CatchSlot getSlot(String slotId) {
        return slots.get(slotId);
    }

    public boolean addSlot(CatchSlot slot) {
        if (slot == null || !slot.isFilled() || slots.containsKey(slot.getSlotId())) return false;
        if (slotIdList.size() >= MAX_SLOTS) return false;
        slots.put(slot.getSlotId(), slot);
        slotIdList.add(slot.getSlotId());
        return true;
    }

    public byte[] getCatchSeed() { return catchSeed; }
}

// ============ RNG utility ============

final class ReelRandom {
    private final long seed;
    private long state;

    public ReelRandom(long seed) {
        this.seed = seed;
        this.state = seed;
    }

    public void mix(long blockNum, String slotId, String angler, int speciesIndex) {
        state = state ^ (blockNum * 0x9e3779b97f4a7c15L);
        state = state ^ slotId.hashCode();
        state = state ^ (long) angler.hashCode();
        state = state + speciesIndex * 0x6c078965L;
        state = (state ^ (state >>> 30)) * 0xbf58476d1ce4e5b9L;
        state = (state ^ (state >>> 27)) * 0x94d049bb133111ebL;
        state = state ^ (state >>> 31);
    }

    public int nextInt(int bound) {
        if (bound <= 0) return 0;
        state = state * 6364136223846793005L + 1442695040888963407L;
        long u = state >>> 33;
        return (int) (u % bound);
    }

    public double nextDouble() {
        state = state * 6364136223846793005L + 1442695040888963407L;
        return ((state >>> 33) & 0x1FFFFFFFFFFL) / (double) (0x1FFFFFFFFFFL);
    }

    public int weightBucket(int maxGrams) {
        int pct = nextInt(100);
        if (pct < 15) return (maxGrams * 25) / 100;
        if (pct < 45) return (maxGrams * 55) / 100;
        if (pct < 80) return (maxGrams * 82) / 100;
        return (maxGrams * 97) / 100;
    }
}

// ============ Main engine ============

public final class BoostaFishaGame {
    public static final String REEL_DOMAIN_HEX = "5e4d3c2b1a09f8e7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c5b4a39281706";
    public static final int FISH_SEASON_BLOCKS = 512;
    public static final int BAIT_CLAIM_PER_CATCH = 75;

    private long currentBlock;
    private long currentSeason;
    private final Lake lake;
    private final Map<String, Angler> anglers;
    private long totalCasts;
    private long totalBaitClaimed;

    public BoostaFishaGame(long genesisBlock, byte[] catchSeed) {
        this.currentBlock = genesisBlock;
        this.currentSeason = 0;
        this.lake = new Lake(catchSeed);
        this.anglers = new HashMap<>();
        this.totalCasts = 0;
        this.totalBaitClaimed = 0;
        seedInitialCatches();
    }

    private void seedInitialCatches() {
        String[] ids = {
            "catch_0_bass", "catch_1_trout", "catch_2_pike", "catch_3_carp",
            "catch_4_perch", "catch_5_salmon", "catch_6_catfish", "catch_7_tuna",
            "catch_8_cod", "catch_9_flounder", "catch_10_mackerel", "catch_11_snapper",
            "catch_12_sturgeon", "catch_13_bluegill", "catch_14_walleye", "catch_15_crappie",
            "catch_16_redfish", "catch_17_sardine"
        };
        int[] speciesIdx = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 0, 1, 2, 3, 4, 5 };
        int[] weights = { 4200, 3100, 5800, 7200, 1800, 4500, 6200, 15000, 3800, 2200, 2600, 4100, 9100, 900, 3400, 1200, 5300, 350 };
        for (int i = 0; i < ids.length; i++) {
            FishSpecies s = FishSpecies.byIndex(speciesIdx[i]);
            lake.addSlot(new CatchSlot(ids[i], s, weights[i], currentBlock, true));
        }
    }

    public long getCurrentBlock() { return currentBlock; }
    public long getCurrentSeason() { return currentSeason; }
    public long getTotalCasts() { return totalCasts; }
    public long getTotalBaitClaimed() { return totalBaitClaimed; }
    public Lake getLake() { return lake; }

    public Angler getOrCreateAngler(String address) {
        return anglers.computeIfAbsent(address, Angler::new);
    }

    public void advanceBlocks(long blocks) {
        this.currentBlock += blocks;
        long newSeasons = currentBlock / FISH_SEASON_BLOCKS;
        if (newSeasons > currentSeason) currentSeason = newSeasons;
    }

    public void advanceSeason() {
        currentSeason++;
    }

    public CastResult castLine(String anglerAddress, String slotId, WeatherCondition weather, TackleType tackle) {
        Angler angler = getOrCreateAngler(anglerAddress);
        if (!angler.canCast(currentBlock, currentSeason))
            return CastResult.cooldownOrCap();
        CatchSlot slot = lake.getSlot(slotId);
        if (slot == null || !slot.isFilled())
            return CastResult.slotEmpty();
        FishSpecies species = slot.getSpecies();
        ReelRandom rng = new ReelRandom(lake.getCatchSeed().length > 0 ? bytesToLong(lake.getCatchSeed()) : 0x5e4d3c2b1a09f8e7L);
        rng.mix(currentBlock, slotId, anglerAddress, species.getIndex());
        int weightGrams = rng.weightBucket(slot.getMaxWeightGrams());
        if (weightGrams < species.getMinGrams()) weightGrams = species.getMinGrams();
        if (weightGrams > slot.getMaxWeightGrams()) weightGrams = slot.getMaxWeightGrams();
        double seasonBonus = SeasonPhase.fromSeasonIndex(currentSeason).speciesBonus(species);
        double weatherMult = weather.getCatchMultiplier();
        double tackleMult = tackle.getWeightBonus();
        double rarity = 0.85 + rng.nextDouble() * 0.30;
        int adjWeight = (int) (weightGrams * seasonBonus * weatherMult * tackleMult);
        Fish fish = new Fish(species, adjWeight, rarity);
        int bait = fish.baitCredits();
        if (bait > BAIT_CLAIM_PER_CATCH) bait = BAIT_CLAIM_PER_CATCH;
        angler.advanceSeason(currentSeason);
        angler.setLastCastBlock(currentBlock);
        angler.addClaimedThisSeason(BAIT_CLAIM_PER_CATCH);
        angler.creditBait(bait);
        CatchRecord record = new CatchRecord(currentBlock, slotId, fish, bait, weather, tackle);
        angler.recordCatch(record);
        totalCasts++;
        totalBaitClaimed += bait;
        currentBlock++;
        return CastResult.success(record, bait);
    }

    private static long bytesToLong(byte[] b) {
        long v = 0;
        for (int i = 0; i < Math.min(8, b.length); i++) v = (v << 8) | (b[i] & 0xff);
        return v;
    }

    // ============ Cast result DTO ============

    public static final class CastResult {
        private final boolean success;
        private final String errorCode;
        private final CatchRecord record;
        private final int baitCredits;

        private CastResult(boolean success, String errorCode, CatchRecord record, int baitCredits) {
            this.success = success;
            this.errorCode = errorCode;
            this.record = record;
            this.baitCredits = baitCredits;
        }

        public static CastResult success(CatchRecord record, int baitCredits) {
            return new CastResult(true, null, record, baitCredits);
        }

        public static CastResult slotEmpty() {
            return new CastResult(false, "SLOT_EMPTY", null, 0);
        }

        public static CastResult cooldownOrCap() {
            return new CastResult(false, "COOLDOWN_OR_CAP", null, 0);
        }

        public boolean isSuccess() { return success; }
        public String getErrorCode() { return errorCode; }
        public CatchRecord getRecord() { return record; }
        public int getBaitCredits() { return baitCredits; }
    }

    // ============ Demo / CLI ============

    // ============ Stats aggregator ============

    public Map<FishSpecies, Integer> getSpeciesCountByAngler(String anglerAddress) {
        Map<FishSpecies, Integer> out = new HashMap<>();
        Angler a = anglers.get(anglerAddress);
        if (a == null) return out;
        for (CatchRecord r : a.getCatchHistory()) {
            FishSpecies s = r.getFish().getSpecies();
            out.merge(s, 1, Integer::sum);
        }
        return out;
    }

    public int getTotalWeightByAngler(String anglerAddress) {
        Angler a = anglers.get(anglerAddress);
        if (a == null) return 0;
        return a.getCatchHistory().stream().mapToInt(r -> r.getFish().getWeightGrams()).sum();
    }

    public List<Angler> getLeaderboardByBait(int topN) {
        return anglers.values().stream()
            .sorted(Comparator.comparingInt(Angler::getBaitBalance).reversed())
            .limit(topN)
            .toList();
    }

    public List<Angler> getLeaderboardByTotalWeight(int topN) {
        return anglers.values().stream()
            .sorted(Comparator.comparingInt(a -> -getTotalWeightByAngler(a.getAddress())))
            .limit(topN)
            .toList();
