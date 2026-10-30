package ru.alisa.adaptation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class Ritual {
    private Ritual() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("adaptation")
            .executes(context -> {
                context.getSource().sendFeedback(
                    () -> Component.literal(Scouts.summary()), false);
                return 1;
            })
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("ritual")
                .executes(context -> {
                    AdapState.reset();
                    context.getSource().sendFeedback(
                        () -> Component.literal("Ритуал очищения завершён: вся адаптация забыта."), false);
                    return 1;
                }))
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("scout")
                .executes(context -> {
                    context.getSource().sendFeedback(
                        () -> Component.literal(Scouts.report()), false);
                    return 1;
                }))
        );
    }
}
