package ru.alisa.adaptation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Всё состояние мода. Сохраняется в JSON в папке мира,
 * чтобы привычки и фазы переживали перезапуск сервера.
 */
public class AdapState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AdapState DATA = new AdapState();
    private static MinecraftServer server = null;

    /** Убийство моба игроком: где и каким "стилем оружия" (для механики обучения врагов). */
    public static class KillRecord {
        public double x, y, z;
        public long tick;
        public KillRecord() {}
        public KillRecord(double x, double y, double z, long tick) {
            this.x = x; this.y = y; this.z = z; this.tick = tick;
        }
    }

    // === Фазы ярости ===
    public String activePhase = "NONE";   // NONE | HUNGER | DARKNESS | MIMICRY
    public long phaseEndsAt = -1;         // абсолютный dayTime, когда фаза кончается
    public long lastPhaseDay = -1000;     // игровой день последней фазы
    public int nextPhaseGap = 4;          // через сколько дней возможна следующая (4..8)
    public String lastPhaseName = "";

    // === Привычки игроков (ключи — UUID игрока в строках) ===
    public Map<String, String> lastFood = new HashMap<>();                 // последняя съеденная еда
    public Map<String, Map<String, Integer>> foodStreaks = new HashMap<>(); // подряд съеденное
    public Map<String, Integer> sleepStreaks = new HashMap<>();            // ночей подряд со сном
    public Map<String, double[]> bedPos = new HashMap<>();                 // координаты постели
    public Map<String, Long> ritualCooldownUntil = new HashMap<>();        // перезарядка алтаря

    // === Обучение врагов (глобально, по миру) ===
    public Map<String, List<KillRecord>> weaponKills = new HashMap<>(); // стиль -> убийства

    // === Разведчик и набеги ===
    public String scoutId = "";        // UUID разведчика (или пусто)
    public long scoutCallAt = -1;      // dayTime, когда разведчик "вызовет" набег
    public double raidX, raidY, raidZ; // точка набега (постель игрока)
    public int raidStrength = 0;       // сила набега = оседлость игрока
    public long scoutCooldownUntil = 0;

    private AdapState() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(srv -> {
            server = srv;
            load(srv);
        });
    }

    public static AdapState get() {
        return DATA;
    }

    public static long dayTime() {
        return server != null && server.overworld() != null ? server.overworld().getDayTime() : 0L;
    }

    public static long gameDay() {
        return dayTime() / 24000L;
    }

    private static Path savePath(MinecraftServer srv) {
        return srv.getWorldPath(LevelResource.ROOT).resolve("adaptation_data.json");
    }

    public static void save() {
        if (server == null) return;
        Path p = savePath(server);
        try {
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p)) {
                GSON.toJson(DATA, w);
            }
        } catch (IOException e) {
            Adaptation.LOGGER.error("[Adaptation] Не удалось сохранить состояние", e);
        }
    }

    public static void load(MinecraftServer srv) {
        Path p = savePath(srv);
        if (!Files.exists(p)) return;
        try (Reader r = Files.newBufferedReader(p)) {
            AdapState loaded = GSON.fromJson(r, AdapState.class);
            DATA.adopt(loaded);
            Adaptation.LOGGER.info("[Adaptation] Состояние мира загружено.");
        } catch (Exception e) {
            Adaptation.LOGGER.error("[Adaptation] Не удалось загрузить состояние, начинаем с нуля", e);
        }
    }

    /** Переносит загруженные данные, не ломая дефолты новых полей. */
    private void adopt(AdapState o) {
        if (o == null) return;
        if (o.activePhase != null) activePhase = o.activePhase;
        phaseEndsAt = o.phaseEndsAt;
        lastPhaseDay = o.lastPhaseDay;
        nextPhaseGap = o.nextPhaseGap > 0 ? o.nextPhaseGap : 4;
        if (o.lastPhaseName != null) lastPhaseName = o.lastPhaseName;
        if (o.lastFood != null) lastFood = o.lastFood;
        if (o.foodStreaks != null) foodStreaks = o.foodStreaks;
        if (o.sleepStreaks != null) sleepStreaks = o.sleepStreaks;
        if (o.bedPos != null) bedPos = o.bedPos;
        if (o.ritualCooldownUntil != null) ritualCooldownUntil = o.ritualCooldownUntil;
        if (o.weaponKills != null) weaponKills = o.weaponKills;
        if (o.scoutId != null) scoutId = o.scoutId;
        scoutCallAt = o.scoutCallAt;
        raidX = o.raidX; raidY = o.raidY; raidZ = o.raidZ;
        raidStrength = o.raidStrength;
        scoutCooldownUntil = o.scoutCooldownUntil;
    }
}
