package ru.alisa.adaptation;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

public class AdapCommand {
    private AdapCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandsRoot.literal("adaptation").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                sendStatus(p);
                return 1;
            }).then(CommandsRoot.literal("reset")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> {
                        AdapState.resetAll();
                        ctx.getSource().sendSuccess(
                                () -> Component.literal("Состояние мода полностью сброшено."), true);
                        return 1;
                    })));
        });
    }

    private static void sendStatus(ServerPlayer p) {
        AdapState s = AdapState.get();
        String key = p.getStringUUID();

        send(p, "=== АДАПТАЦИЯ ===", ChatFormatting.BOLD);

        String phaseText = switch (s.activePhase) {
            case "HUNGER" -> "ГОЛОД (активна)";
            case "DARKNESS" -> "МГЛА (активна)";
            case "MIMICRY" -> "МИМИКРИЯ (активна)";
            default -> "затишье (следующая возможна через "
                    + Math.max(0, s.lastPhaseDay + s.nextPhaseGap - AdapState.gameDay()) + " дн.)";
        };
        send(p, "Фаза ярости: " + phaseText, ChatFormatting.RED);

        int base = s.daysAtBase.getOrDefault(key, 0);
        send(p, "Оседлость: " + base + " ночей подряд у одной постели"
                + (base >= 3 ? " — мир заметил твой дом" : ""), ChatFormatting.YELLOW);

        Map<String, Integer> fs = s.foodStreaks.get(key);
        if (fs != null && !fs.isEmpty()) {
            StringBuilder sb = new StringBuilder("Пищевые привычки:");
            for (Map.Entry<String, Integer> e : fs.entrySet()) {
                if (e.getValue() >= 2) sb.append(" ").append(shortId(e.getKey())).append(" x").append(e.getValue());
            }
            send(p, sb.toString(), ChatFormatting.YELLOW);
        }

        StringBuilder sb = new StringBuilder("Уроки мира (зоны, где мобы учились):");
        if (s.weaponKills.isEmpty()) {
            sb.append(" пока ничего");
        } else {
            for (Map.Entry<String, List<AdapState.KillRecord>> e : s.weaponKills.entrySet()) {
                sb.append(" ").append(e.getKey()).append(":").append(e.getValue().size());
            }
        }
        send(p, sb.toString(), ChatFormatting.YELLOW);

        long now = AdapState.dayTime();
        long cd = s.ritualCooldownUntil.getOrDefault(key, 0L);
        if (now < cd) {
            send(p, "Алтарь Забвения: перезарядка ещё ~" + ((cd - now) / 24000L + 1) + " дн.", ChatFormatting.GRAY);
        } else {
            send(p, "Алтарь Забвения готов: магнитный камень + жемчуг Края (крадучись).", ChatFormatting.GOLD);
        }
        send(p, "Мир реагирует на повторяемость. Меняй тактику — сбрасывай привычки.", ChatFormatting.GRAY);
    }

    private static void send(ServerPlayer p, String text, ChatFormatting color) {
        p.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    private static String shortId(String id) {
        int i = id.indexOf(':');
        String s = i >= 0 ? id.substring(i + 1) : id;
        return s.replace("minecraft_", "");
    }
}
