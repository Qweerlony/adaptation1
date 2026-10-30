package ru.alisa.adaptation;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Механика 4 "Фазы Ярости": раз в 4-8 игровых дней мир входит в одну из фаз на 2-4 игровых часа:
 * - ГОЛОД: насыщение тает быстрее; привычка к одной еде усиливает эффект (связка с механикой 1).
 * - МГЛА: на игроков регулярно накатывает мгла.
 * - МИМИКРИЯ: часть сундуков "подражает" — при открытии вылезает засада.
 * Начало фазы предвещается коротким накатом мглы — игрок учится узнавать предвестник.
 */
public class Phases {
    private static final Random RNG = new Random();
    private static final List<String> PHASES = List.of("HUNGER", "DARKNESS", "MIMICRY");
    private static final long MIN_DURATION = 2400L; // 2 игровых часа
    private static final long MAX_DURATION = 4800L; // 4 игровых часа

    private Phases() {}

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level)) return InteractionResult.PASS;
            AdapState s = AdapState.get();
            if (!"MIMICRY".equals(s.activePhase)) return InteractionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            BlockState st = level.getBlockState(pos);
            if (!st.is(BlockTags.CHESTS)) return InteractionResult.PASS;
            if (RNG.nextFloat() >= 0.22F) return InteractionResult.PASS;

            // Сундук "ожил": вылезает засада
            int count = 2 + RNG.nextInt(3);
            for (int i = 0; i < count; i++) {
                Silverfish fish = new Silverfish(level);
                double ang = Math.PI * 2.0 * RNG.nextDouble();
                double r = 1.5 + RNG.nextDouble() * 2.0;
                fish.setPos(pos.getX() + 0.5 + Math.cos(ang) * r,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5 + Math.sin(ang) * r);
                level.addFreshEntity(fish);
            }
            player.sendSystemMessage(Component.literal(
                    "Сундук не сундук. Он был здесь слишком долго.")
                    .withStyle(ChatFormatting.DARK_RED));
            return InteractionResult.PASS;
        });
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (level == null) return;
        AdapState s = AdapState.get();
        long now = level.getDayTime();

        // Конец фазы
        if (!"NONE".equals(s.activePhase) && now >= s.phaseEndsAt) {
            s.activePhase = "NONE";
            s.phaseEndsAt = -1;
            announce(server, "Фаза Ярости стихла. Мир переваривает увиденное.", ChatFormatting.GRAY);
        }

        // Планирование следующей фазы
        if ("NONE".equals(s.activePhase)) {
            long day = now / 24000L;
            if (day >= s.lastPhaseDay + s.nextPhaseGap) {
                startPhase(server, s, now, day);
            }
        }

        // Периодические эффекты активной фазы (каждые 600 тиков)
        if (!"NONE".equals(s.activePhase) && now % 600L == 0L) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.level() != level) continue;
                applyPhaseEffect(s, p);
            }
        }
    }

    private static void startPhase(MinecraftServer server, AdapState s, long now, long day) {
        String pick;
        do {
            pick = PHASES.get(RNG.nextInt(PHASES.size()));
        } while (pick.equals(s.lastPhaseName) && PHASES.size() > 1);

        s.lastPhaseName = pick;
        s.activePhase = pick;
        s.phaseEndsAt = now + MIN_DURATION + RNG.nextLong(MAX_DURATION - MIN_DURATION);
        s.lastPhaseDay = day;
        s.nextPhaseGap = 4 + RNG.nextInt(5);

        switch (pick) {
            case "HUNGER" -> announce(server,
                    "ФАЗА ЯРОСТИ: ГОЛОД. Воздух стал голодным. Еда теряет вкус, а сытость тает быстрее.",
                    ChatFormatting.RED);
            case "DARKNESS" -> announce(server,
                    "ФАЗА ЯРОСТИ: МГЛА. Свет начал уходить. Что-то смотрит из темноты.",
                    ChatFormatting.DARK_PURPLE);
            case "MIMICRY" -> announce(server,
                    "ФАЗА ЯРОСТИ: МИМИКРИЯ. Сундуки начали подражать. Проверь, все ли они настоящие.",
                    ChatFormatting.GOLD);
        }

        // Предвестник: короткий накат мглы — игрок учится узнавать его
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
        }
    }

    private static void applyPhaseEffect(AdapState s, ServerPlayer p) {
        switch (s.activePhase) {
            case "HUNGER" -> {
                int extraAmp = topFoodStreak(s, p) >= 5 ? 1 : 0; // привычка к одной еде усиливает фазу
                p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 300, extraAmp));
            }
            case "DARKNESS" -> p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 300, 0));
            default -> {}
        }
    }

    private static int topFoodStreak(AdapState s, ServerPlayer p) {
        Map<String, Integer> m = s.foodStreaks.get(p.getStringUUID());
        if (m == null || m.isEmpty()) return 0;
        int best = 0;
        for (int v : m.values()) if (v > best) best = v;
        return best;
    }

    private static void announce(MinecraftServer server, String text, ChatFormatting color) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(text).withStyle(color));
        }
    }
}
