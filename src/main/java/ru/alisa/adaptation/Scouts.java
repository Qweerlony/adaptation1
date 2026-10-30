package ru.alisa.adaptation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class Scouts {
    private Scouts() {}

    public static String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Прожито дней: ").append(AdapState.day());
        List<Map.Entry<String, Integer>> top = top(AdapState.deathsByThreat, 5);
        if (top.isEmpty()) {
            sb.append("\nУгроз пока не зафиксировано.");
        } else {
            sb.append("\nТоп угроз:");
            for (Map.Entry<String, Integer> e : top) {
                sb.append("\n - ").append(e.getKey())
                  .append(": смертей ").append(e.getValue())
                  .append(", адаптация ").append(AdapState.level(e.getKey())).append("/5");
            }
        }
        return sb.toString();
    }

    public static String report() {
        StringBuilder sb = new StringBuilder("Опыт мобов:");
        List<Map.Entry<String, Integer>> top = top(AdapState.mobExperience, 5);
        if (top.isEmpty()) {
            sb.append("\nМобы пока ничему не научились.");
        } else {
            for (Map.Entry<String, Integer> e : top) {
                sb.append("\n - ").append(e.getKey()).append(": ").append(e.getValue());
            }
        }
        return sb.toString();
    }

    private static List<Map.Entry<String, Integer>> top(Map<String, Integer> map, int limit) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(map.entrySet());
        list.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()));
        return list.size() > limit ? list.subList(0, limit) : list;
    }
}
