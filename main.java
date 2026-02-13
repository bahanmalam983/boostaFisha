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
