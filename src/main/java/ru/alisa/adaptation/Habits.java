package ru.alisa.adaptation;

import net.fabricmc.fabric.api.event.player.EntitySleepEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Механика 1 "Привычка": мир следит за едой и сном игрока.
 * - Одна и та же еда подряд -> "пресная" (эффект голода, нарастает).
 * - Сон на одной постели каждую ночь -> "оседлость" + шанс кошмарной засады при пробуждении.
 */
public class Habits {
    // playerUUID -> [itemId, startTick, displayName]
    private static final Map<UUID, String[]> pendingUse = new HashMap<>();

    private Habits() {}

    public static void register() {
        // Игрок начал "использовать" предмет — если это еда, запоминаем
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClientSide()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.has(DataComponents.FOOD)) {
                    pendingUse.put(player.getUUID(), new String[]{
                            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                            String.valueOf(world.getDayTime()),
                            stack.getHoverName().getString()
                    });
                }
            }
            return InteractionResult.PASS;
        });

        EntitySleepEvents.STOP_SLEEPING.register(Habits::onWake);
    }

    /** Догоняет "доедания": если игрок перестал есть — применяем эффект. */
    public static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String[] pend = pendingUse.get(p.getUUID());
            if (pend == null) continue;
            if (!p.isUsingItem()) {
                pendingUse.remove(p.getUUID());
                long start = Long.parseLong(pend[1]);
                if (p.level().getDayTime() - start >= 10) {
                    applyFood(p, pend[0], pend[2]);
                }
            }
        }
    }

    private static void applyFood(ServerPlayer p, String itemId, String display) {
        AdapState s = AdapState.get();
        String key = p.getStringUUID();
        Map<String, Integer> streaks = s.foodStreaks.computeIfAbsent(key, k -> new HashMap<>());
        String prev = s.lastFood.get(key);
        int streak = itemId.equals(prev) ? streaks.getOrDefault(itemId, 0) + 1 : 1;
        streaks.put(itemId, streak);
        s.lastFood.put(key, itemId);

        if (streak >= 5 && (streak - 5) % 3 == 0) { // эскалация на 5, 8, 11, 14...
            int amp = Math.min(3, (streak - 5) / 3);
            p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, amp));
            p.sendSystemMessage(Component.literal(
                    "Мир адаптировался: «" + display + "» больше не насыщает как прежняя. Разнообразь стол.")
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static void onWake(LivingEntity entity, BlockPos pos) {
        if (!(entity instanceof ServerPlayer p)) return;
        Level level = p.level();
        if (level.isClientSide()) return;

        AdapState s = AdapState.get();
        String key = p.getStringUUID();

        double[] prev = s.bedPos.get(key);
        int streak = 1;
        if (prev != null) {
            double dx = prev[0] - pos.getX();
            double dy = prev[1] - pos.getY();
            double dz = prev[2] - pos.getZ();
            if (dx * dx + dy * dy + dz * dz < 9216.0) { // та же постель (радиус 96)
                streak = s.sleepStreaks.getOrDefault(key, 0) + 1;
            }
        }
        s.bedPos.put(key, new double[]{pos.getX(), pos.getY(), pos.getZ()});
        s.sleepStreaks.put(key, streak);
        s.daysAtBase.put(key, streak); // оседлость для механики набегов

        if (streak >= 3) {
            float chance = Math.min(0.35F + 0.05F * (streak - 3), 0.7F);
            if (p.getRandom().nextFloat() < chance) {
                spawnNightmare(level, pos, streak);
                p.sendSystemMessage(Component.literal(
                        "Сон был слишком глубоким. Кто-то уже стоял у постели.")
                        .withStyle(ChatFormatting.DARK_RED));
            }
        }
    }

    private static void spawnNightmare(Level level, BlockPos pos, int streak) {
        int count = Math.min(6, 1 + streak);
        boolean day = level.isDay();
        for (int i = 0; i < count; i++) {
            Monster mob;
            if (day) {
                mob = new Husk(level); // днём — утопленники-«тельца», не сгорают
            } else if (i == count - 1) {
                mob = new Skeleton(level);
            } else {
                mob = new Zombie(level);
            }
            double ang = Math.PI * 2.0 * i / count;
            double r = 3.0 + level.getRandom().nextDouble() * 5.0;
            mob.setPos(pos.getX() + Math.cos(ang) * r, pos.getY() + 1.0, pos.getZ() + Math.sin(ang) * r);
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
        }
    }
}
