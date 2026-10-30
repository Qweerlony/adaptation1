package ru.alisa.adaptation;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class Habits {
    private Habits() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Habits::onTick);
    }

    private static void onTick(MinecraftServer server) {
        long day = AdapState.day();
        if (day != AdapState.countedDay) {
            AdapState.countedDay = day;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                String name = player.getName().getString();
                AdapState.daysAtBase.merge(name, 1, Integer::sum);
            }
        }
    }
}
