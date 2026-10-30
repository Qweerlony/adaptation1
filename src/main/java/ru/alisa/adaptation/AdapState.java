package ru.alisa.adaptation;

import java.util.HashMap;
import java.util.Map;

public final class AdapState {
    private AdapState() {}

    public static final Map<String, Integer> deathsByThreat = new HashMap<>();
    public static final Map<String, Integer> adaptation = new HashMap<>();
    public static final Map<String, Integer> mobExperience = new HashMap<>();
    public static final Map<String, Integer> daysAtBase = new HashMap<>();

    public static long ticks = 0;
    public static long countedDay = -1;

    public static void tick() {
        ticks++;
    }

    public static long day() {
        return ticks / 24000;
    }

    public static void bump(Map<String, Integer> map, String key, int max) {
        map.merge(key, 1, Integer::sum);
        if (map.get(key) > max) {
            map.put(key, max);
        }
    }

    public static int level(String threat) {
        return Math.min(5, adaptation.getOrDefault("adapt_" + threat, 0) / 20);
    }

    public static void reset() {
        deathsByThreat.clear();
        adaptation.clear();
        daysAtBase.clear();
    }
}
