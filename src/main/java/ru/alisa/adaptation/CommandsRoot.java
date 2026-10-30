package ru.alisa.adaptation;

/** Короткий алиас для literal(), чтобы код команды был чище. */
final class CommandsRoot {
    private CommandsRoot() {}
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
    literal(String name) {
        return net.minecraft.commands.Commands.literal(name);
    }
}
