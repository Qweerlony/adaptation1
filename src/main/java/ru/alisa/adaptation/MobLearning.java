package ru.alisa.adaptation;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public final class MobLearning {
    private MobLearning() {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(MobLearning::onDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register(MobLearning::onDeath);
    }

    private static boolean onDamage(LivingEntity target, DamageSource source, float amount) {
        if (target instanceof ServerPlayer) {
            AdapState.bump(AdapState.adaptation, "adapt_" + threatOf(source), 100);
        } else {
            AdapState.bump(AdapState.mobExperience, target.getClass().getSimpleName(), 50);
        }
        return true;
    }

    private static void onDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof ServerPlayer) {
            AdapState.deathsByThreat.merge(threatOf(source), 1, Integer::sum);
        }
    }

    private static String threatOf(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer) {
            return "pvp";
        }
        if (source.getEntity() != null) {
            return source.getEntity().getClass().getSimpleName().toLowerCase();
        }
        return "environment";
    }
}
