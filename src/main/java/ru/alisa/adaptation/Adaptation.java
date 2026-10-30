package ru.alisa.adaptation;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Adaptation implements ModInitializer {
    public static final String MOD_ID = "adaptation";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private int saveCounter = 0;

    @Override
    public void onInitialize() {
        LOGGER.info("[Adaptation] Мир начинает тебя запоминать...");

        AdapState.init();
        Habits.register();
        MobLearning.register();
        Scouts.register();
        Phases.register();
        Ritual.register();
        AdapCommand.register();

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> AdapState.save());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Phases.tick(server);
            Scouts.tick(server);
            Habits.tick(server);
            if (++saveCounter >= 1200) { // автосохранение раз в ~1 минуту
                saveCounter = 0;
                AdapState.save();
            }
        });
    }
}
