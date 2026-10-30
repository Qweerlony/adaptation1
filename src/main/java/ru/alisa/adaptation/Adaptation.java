package ru.alisa.adaptation;

import net.fabricmc.api.ModInitializer;

public class Adaptation implements ModInitializer {
    @Override
    public void onInitialize() {
        MobLearning.register();
        Habits.register();
        Ritual.register();
    }
}
