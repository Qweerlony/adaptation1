package ru.alisa.adaptation;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.living.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Механика 2 "Враги учатся":
 * - Каждое убийство моба игроком записывается как "урок стиля оружия" в этой точке.
 * - Новые мобы рядом с "уроками" получают защиту от этого стиля (броня + шанс полностью
 *   отразить удар) и становятся шустрее.
 * - Записи тают со временем: смени оружие — и зона "забудет".
 */
public class MobLearning {
    private static final long RECORD_LIFETIME = 12000L; // 10 реальных минут
    private static final int    RECORD_LIMIT    = 64;
    private static final double LEARN_RADIUS    = 32.0;
    private static final int    LEARN_THRESHOLD = 4;

    // mobUUID -> (стиль оружия -> уровень обучения 1..5)
    private static final Map<UUID, Map<String, Integer>> learned = new HashMap<>();

    private MobLearning() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(MobLearning::onDeath);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(MobLearning::onDamage);
        ServerEntityEvents.ENTITY_LOAD.register(MobLearning::onSpawn);
    }

    private static void onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof Monster) || !(source.getEntity() instanceof Player player)) return;
        if (!(entity.level() instanceof ServerLevel level)) return;

        String cat = categoryOf(player.getMainHandItem(), source);
        long now = level.getDayTime();
        List<AdapState.KillRecord> list = AdapState.get().weaponKills
                .computeIfAbsent(cat, k -> new ArrayList<>());
        list.add(new AdapState.KillRecord(entity.getX(), entity.getY(), entity.getZ(), now));

        Iterator<AdapState.KillRecord> it = list.iterator();
        while (it.hasNext()) {
            if (now - it.next().tick > RECORD_LIFETIME) it.remove();
        }
        while (list.size() > RECORD_LIMIT) list.remove(0);

        learned.remove(entity.getUUID());
    }

    private static void onSpawn(Entity entity, ServerLevel world) {
        if (!(entity instanceof Monster mob)) return;
        AdapState s = AdapState.get();
        long now = world.getDayTime();

        Map<String, Integer> found = new HashMap<>();
        for (Map.Entry<String, List<AdapState.KillRecord>> e : s.weaponKills.entrySet()) {
            int count = 0;
            for (AdapState.KillRecord r : e.getValue()) {
                if (now - r.tick > RECORD_LIFETIME) continue;
                double dx = r.x - mob.getX();
                double dy = r.y - mob.getY();
                double dz = r.z - mob.getZ();
                if (dx * dx + dy * dy + dz * dz <= LEARN_RADIUS * LEARN_RADIUS) count++;
            }
            if (count >= LEARN_THRESHOLD) {
                found.put(e.getKey(), Math.min(5, count - LEARN_THRESHOLD + 1));
            }
        }
        if (found.isEmpty()) return;

        for (Map.Entry<String, Integer> e : found.entrySet()) {
            applyModifiers(mob, e.getKey(), e.getValue());
        }
        learned.put(mob.getUUID(), found);
    }

    private static void applyModifiers(Monster mob, String cat, int level) {
        String lower = cat.toLowerCase(Locale.ROOT);

        AttributeInstance armor = mob.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("adaptation", "learned_armor_" + lower);
            if (!armor.hasModifier(id)) {
                armor.addPermanentModifier(new AttributeModifier(id, 3.0 * level,
                        AttributeModifier.Operation.ADD_VALUE));
            }
        }
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("adaptation", "learned_speed_" + lower);
            if (!speed.hasModifier(id)) {
                speed.addPermanentModifier(new AttributeModifier(id, 0.02 * level,
                        AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    private static boolean onDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof Monster mob) || !(source.getEntity() instanceof Player player)) return true;
        Map<String, Integer> m = learned.get(mob.getUUID());
        if (m == null || m.isEmpty()) return true;

        String cat = categoryOf(player.getMainHandItem(), source);
        Integer lvl = m.get(cat);
        if (lvl == null) return true;

        double chance = Math.min(0.12 + 0.08 * lvl, 0.55);
        if (mob.getRandom().nextDouble() < chance) {
            if (mob.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CRIT,
                        mob.getX(), mob.getY() + 0.9, mob.getZ(),
                        8, 0.3, 0.3, 0.3, 0.15);
            }
            if (player.getRandom().nextInt(4) == 0) {
                player.sendSystemMessage(Component.literal(
                        "Он изучил твой стиль боя — удар не пробивает!")
                        .withStyle(ChatFormatting.DARK_RED));
            }
            return false;
        }
        return true;
    }

    private static String categoryOf(ItemStack weapon, DamageSource source) {
        if (source.getDirectEntity() instanceof AbstractArrow) return "RANGED";
        Item item = weapon.getItem();
        if (item instanceof SwordItem) return "SWORD";
        if (item instanceof AxeItem) return "AXE";
        if (item instanceof TridentItem) return "RANGED";
        if (item instanceof MaceItem) return "MACE";
        if (item instanceof PickaxeItem || item instanceof ShovelItem || item instanceof HoeItem) return "TOOL";
        return "OTHER";
    }
}
