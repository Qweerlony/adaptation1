package ru.alisa.adaptation;

import net.fabricmc.fabric.api.event.living.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;
import java.util.UUID;

/**
 * Механика 3 "Осада гнезда":
 * - Игрок, 3+ ночей подряд спящий на одной постели, становится "осёдлым".
 * - Ночью у его базы появляется Разведчик (бесшумный зависший фантом, не атакует).
 * - Убил разведчика — набег откладывается на 2 дня; прозевал на 5 минут — начинается набег,
 *   сила которого растёт с оседлостью.
 */
public class Scouts {
    private static final Random RNG = new Random();
    private static final long SCOUT_LIFETIME = 6000L;  // 5 реальных минут на "разведку"
    private static final long SCOUT_COOLDOWN = 48000L; // 2 игровых дня затишья после набега

    private Scouts() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(Scouts::onScoutDeath);
    }

    private static void onScoutDeath(LivingEntity entity, DamageSource source) {
        AdapState s = AdapState.get();
        if (s.scoutId.isEmpty() || !entity.getUUID().toString().equals(s.scoutId)) return;

        s.scoutId = "";
        s.scoutCallAt = -1;
        s.scoutCooldownUntil = AdapState.dayTime() + SCOUT_COOLDOWN;

        if (source.getEntity() instanceof ServerPlayer p) {
            p.sendSystemMessage(Component.literal(
                    "Разведчик повержен. Набег отложен — но мир не забыл, где ты живёшь.")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (level == null) return;
        AdapState s = AdapState.get();
        long now = level.getDayTime();

        // 1) Время разведчика вышло — мир "выучил" базу, запускаем набег
        if (!s.scoutId.isEmpty() && now >= s.scoutCallAt) {
            try {
                Entity scout = level.getEntity(UUID.fromString(s.scoutId));
                if (scout != null) scout.discard();
            } catch (IllegalArgumentException ignored) {}
            doRaid(level, s);
            s.scoutId = "";
            s.scoutCallAt = -1;
            s.scoutCooldownUntil = now + SCOUT_COOLDOWN;
            return;
        }

        // 2) Попытка выпустить нового разведчика
        if (!s.scoutId.isEmpty() || now < s.scoutCooldownUntil) return;
        if (!isNight(now)) return;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() != level) continue;
            int base = s.daysAtBase.getOrDefault(p.getStringUUID(), 0);
            if (base < 3) continue;
            double[] bed = s.bedPos.get(p.getStringUUID());
            if (bed == null) continue;

            double ang = RNG.nextDouble() * Math.PI * 2.0;
            double r = 40.0 + RNG.nextDouble() * 20.0;
            double x = bed[0] + Math.cos(ang) * r;
            double z = bed[2] + Math.sin(ang) * r;
            double y = bed[1] + 8.0;

            Phantom scout = new Phantom(level);
            scout.setNoAi(true);
            scout.setPos(x, y, z);
            scout.setCustomName(Component.literal("Разведчик"));
            scout.setCustomNameVisible(true);
            scout.setPersistenceRequired();
            level.addFreshEntity(scout);

            s.scoutId = scout.getUUID().toString();
            s.scoutCallAt = now + SCOUT_LIFETIME;
            s.raidX = bed[0];
            s.raidY = bed[1];
            s.raidZ = bed[2];
            s.raidStrength = Math.min(10, base);

            p.sendSystemMessage(Component.literal(
                    "В небе завис Разведчик. Он запоминает твой дом. Убей его — или через 5 минут начнётся набег.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
    }

    private static void doRaid(ServerLevel level, AdapState s) {
        int strength = s.raidStrength;
        int count = Math.min(24, 4 + 2 * strength);

        for (int i = 0; i < count; i++) {
            double ang = RNG.nextDouble() * Math.PI * 2.0;
            double r = 8.0 + RNG.nextDouble() * 16.0;
            int x = (int) Math.round(s.raidX + Math.cos(ang) * r);
            int z = (int) Math.round(s.raidZ + Math.sin(ang) * r);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);

            int roll = RNG.nextInt(100);
            LivingEntity mob;
            if (strength >= 8 && roll < 15) {
                mob = new Witch(level);
            } else if (strength >= 5 && roll < 35) {
                mob = new Creeper(level);
            } else if (roll < 70) {
                mob = new Husk(level);
            } else {
                mob = new Spider(level);
            }

            mob.setPos(x + 0.5, y + 1.0, z + 0.5);
            if (mob instanceof Monster m) m.setPersistenceRequired();
            level.addFreshEntity(mob);
        }

        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(Component.literal(
                    "НАБЕГ! Мир выучил, где ты живёшь. Они идут.")
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    private static boolean isNight(long now) {
        long t = Math.floorMod(now, 24000L);
        return t >= 13000L && t <= 23000L;
    }
}
