package ru.alisa.adaptation;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Random;
import java.util.UUID;

/**
 * Механика 5 "Алтарь Забвения":
 * Крадучись использовать жемчуг Края на магнитном камне.
 * Сбрасывает твои привычки, зоны обучения врагов и откладывает фазы ярости.
 * Это не победа, а покупка нового старта: мир начнёт учиться заново.
 */
public class Ritual {
    private static final Random RNG = new Random();
    private static final long COOLDOWN = 96000L; // 4 игровых дня

    private Ritual() {}

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            BlockPos pos = hitResult.getBlockPos();
            BlockState st = world.getBlockState(pos);
            ItemStack held = player.getItemInHand(hand);

            if (!st.is(Blocks.LODESTONE) || !held.is(Items.ENDER_PEARL) || !player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }

            // На клиенте только отменяем бросок жемчуга, всю работу делает сервер
            if (world.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (!(world instanceof ServerLevel level)) return InteractionResult.PASS;

            AdapState s = AdapState.get();
            String key = player.getStringUUID();
            long now = level.getDayTime();

            long until = s.ritualCooldownUntil.getOrDefault(key, 0L);
            if (now < until) {
                player.sendSystemMessage(Component.literal(
                        "Алтарь молчит. Он устал не меньше, чем ты.")
                        .withStyle(ChatFormatting.GRAY));
                return InteractionResult.PASS;
            }

            if (!player.getAbilities().instabuild) held.shrink(1);

            // Забвение привычек игрока и "уроков" мира
            s.lastFood.remove(key);
            s.foodStreaks.remove(key);
            s.sleepStreaks.remove(key);
            s.daysAtBase.remove(key);
            s.bedPos.remove(key);
            s.weaponKills.clear();

            // Фазы откладываются
            s.activePhase = "NONE";
            s.phaseEndsAt = -1;
            s.lastPhaseDay = now / 24000L;
            s.nextPhaseGap = 6 + RNG.nextInt(4);

            // Разведчик перестаёт существовать
            if (!s.scoutId.isEmpty()) {
                try {
                    Entity scout = level.getEntity(UUID.fromString(s.scoutId));
                    if (scout != null) scout.discard();
                } catch (IllegalArgumentException ignored) {}
                s.scoutId = "";
                s.scoutCallAt = -1;
            }
            s.scoutCooldownUntil = now + COOLDOWN;
            s.ritualCooldownUntil.put(key, now + COOLDOWN);

            player.sendSystemMessage(Component.literal(
                    "Мир на мгновение ослеп. Твои привычки забыты — но он начнёт учиться заново.")
                    .withStyle(ChatFormatting.GOLD));
            return InteractionResult.SUCCESS;
        });
    }
}
