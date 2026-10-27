package faygolover.rpculinary.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.event.RpTimeSkipEvent;
import faygolover.rpculinary.capability.NutritionData;
import faygolover.rpculinary.core.Metabolism;
import faygolover.rpculinary.core.NutritionState;
import faygolover.rpculinary.core.Nutrients;
import faygolover.rpculinary.core.Settings;
import faygolover.rpculinary.integration.Lso;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * {@code /rpculinary} (ГМ, уровень 2): посмотреть питание игрока, выставить сытость, воду, нутриенты, вес и жир.
 * Прокрутка голода — {@code /rptime sleep|skip} (RP Core, {@link #onTimeSkip}); {@code audit} — отладочная.
 */
public final class CulinaryCommands {
    private CulinaryCommands() {}

    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
        set.then(Commands.argument("targets", EntityArgument.players())
                .then(value("satiety", 0, 175, (m, v) -> {
                    Settings s = Settings.get();
                    double kcal = v / 100 * s.reserveKcal;
                    m.energy = Math.min(s.reserveKcal, kcal);
                    m.stomach = Math.max(0, kcal - s.reserveKcal);
                    m.starvingSeconds = 0;
                }))
                .then(value("water", 0, 100, (m, v) -> m.water = v))
                .then(value("protein", 0, 120, (m, v) -> m.nutrients[NutritionState.PROTEIN] = v))
                .then(value("fat", 0, 120, (m, v) -> m.nutrients[NutritionState.FAT] = v))
                .then(value("carbs", 0, 120, (m, v) -> m.nutrients[NutritionState.CARBS] = v))
                .then(value("vitamins", 0, 120, (m, v) -> m.nutrients[NutritionState.VITAMINS] = v))
                .then(value("weight", 30, 250, (m, v) -> m.setBody(v, m.fatPercent(), Settings.get())))
                .then(value("bodyfat", 2, 60, (m, v) -> m.setBody(m.weightKg(), v, Settings.get()))));
        d.register(Commands.literal("rpculinary").requires(src -> src.hasPermission(2))
                .then(Commands.literal("status")
                        .executes(c -> status(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> status(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(set)
                .then(Commands.literal("audit").requires(src -> RpCoreAPI.debugCommands())
                        .executes(c -> audit(c.getSource(), ""))
                        .then(Commands.argument("namespace", StringArgumentType.word()).executes(c -> audit(c.getSource(),
                                StringArgumentType.getString(c, "namespace")))))
                .then(Commands.literal("notes").then(Commands.argument("group", StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                Exotic.GROUPS.stream().filter(g -> Exotic.learnable(Exotic.index(g))), b))
                        .executes(c -> notes(c.getSource(), StringArgumentType.getString(c, "group"),
                                java.util.List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players()).executes(c -> notes(c.getSource(),
                                StringArgumentType.getString(c, "group"), EntityArgument.getPlayers(c, "targets"))))))
                .then(Commands.literal("reset").then(Commands.argument("targets", EntityArgument.players()).executes(c -> {
                    Collection<ServerPlayer> ps = EntityArgument.getPlayers(c, "targets");
                    for (ServerPlayer sp : ps) {
                        NutritionState m = CulinaryService.state(sp);
                        if (m == null) continue;
                        m.init(Settings.get(), m.weightKg(), m.fatPercent());
                        var nd = faygolover.rpculinary.capability.NutritionData.get(sp);
                        if (nd != null) {
                            nd.humanMeals = 0;
                            nd.kuruSeconds = 0;
                        }
                        changed(sp);
                    }
                    c.getSource().sendSuccess(() -> Component.translatable("rpculinary.cmd.reset", ps.size()), true);
                    return ps.size();
                }))));
    }

    /** Выдать книгу «Записи исследований» группы. */
    private static int notes(CommandSourceStack src, String group, Collection<ServerPlayer> targets) {
        int g = Exotic.index(group);
        if (!Exotic.learnable(g)) {
            src.sendFailure(Component.translatable("rpculinary.cmd.notes_unknown", group));
            return 0;
        }
        for (ServerPlayer sp : targets) {
            var st = faygolover.rpculinary.item.ResearchNotes.of(g);
            if (!sp.getInventory().add(st)) sp.drop(st, false);
        }
        src.sendSuccess(() -> Component.translatable("rpculinary.cmd.notes", faygolover.rpculinary.item.ResearchNotes.groupName(g), targets.size()), true);
        return targets.size();
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> value(String name, double min, double max,
                                                                                         BiConsumer<NutritionState, Double> apply) {
        return Commands.literal(name).then(Commands.argument("value", DoubleArgumentType.doubleArg(min, max)).executes(c -> {
            double v = DoubleArgumentType.getDouble(c, "value");
            Collection<ServerPlayer> ps = EntityArgument.getPlayers(c, "targets");
            for (ServerPlayer sp : ps) {
                NutritionState m = CulinaryService.state(sp);
                if (m == null) continue;
                if (name.equals("water") && Lso.hydration(sp) >= 0) Lso.addWater(sp, v - Lso.hydration(sp) * 100);
                else apply.accept(m, v);
                changed(sp);
            }
            c.getSource().sendSuccess(() -> Component.translatable("rpculinary.cmd.set", name, fmt(v), ps.size()), true);
            return ps.size();
        }));
    }

    /**
     * Аудит состава еды: сколько блюд посчитано по датапаку, по рецептам и только оценено по ванильной сытости.
     * Оценённые — кандидаты в датапак {@code rpculinary/nutrition}. Полный список — в {@code rpculinary-audit.txt}.
     */
    private static int audit(CommandSourceStack src, String namespace) {
        var server = src.getServer();
        java.util.EnumMap<NutritionTable.Source, Integer> counts = new java.util.EnumMap<>(NutritionTable.Source.class);
        java.util.List<String> estimated = new java.util.ArrayList<>();
        StringBuilder file = new StringBuilder("# RP Culinary: состав еды (id, источник, ккал, Б, Ж, У, вит., вода)").append(System.lineSeparator());
        for (net.minecraft.world.item.Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
            var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
            if (id == null || (!namespace.isEmpty() && !id.getNamespace().equals(namespace))) continue;
            NutritionTable.Source src0 = NutritionTable.source(server, item);
            if (src0 == null) continue;
            counts.merge(src0, 1, Integer::sum);
            var f = NutritionTable.get(server, item);
            file.append(String.format(Locale.ROOT, "%s;%s;%.0f;%.1f;%.1f;%.1f;%.1f;%.0f%n", id, src0, f.kcal(), f.protein(), f.fat(),
                    f.carbs(), f.vitamins(), f.water()));
            if (src0 == NutritionTable.Source.ESTIMATE) estimated.add(id.toString());
        }
        try {
            java.nio.file.Files.writeString(server.getServerDirectory().toPath().resolve("rpculinary-audit.txt"), file.toString());
        } catch (java.io.IOException ex) {
            src.sendFailure(Component.literal(ex.toString()));
        }
        src.sendSuccess(() -> Component.translatable("rpculinary.cmd.audit", counts.getOrDefault(NutritionTable.Source.RULE, 0),
                counts.getOrDefault(NutritionTable.Source.RECIPE, 0), counts.getOrDefault(NutritionTable.Source.ESTIMATE, 0))
                .withStyle(ChatFormatting.GOLD), false);
        if (!estimated.isEmpty()) {
            String list = String.join(", ", estimated.subList(0, Math.min(30, estimated.size()))) + (estimated.size() > 30 ? "…" : "");
            src.sendSuccess(() -> Component.translatable("rpculinary.cmd.audit_estimated", list).withStyle(ChatFormatting.GRAY), false);
        }
        return estimated.size();
    }

    /** Команды для общего списка {@code /rpcore commands}. */
    public static void describe() {
        String m = faygolover.rpculinary.RpCulinary.MODID;
        RpCoreAPI.registerCommand(m, "/rpculinary status [игрок]", "rpculinary.help.status", 2, false);
        RpCoreAPI.registerCommand(m, "/rpculinary set <игроки> satiety|water|protein|fat|carbs|vitamins|weight|bodyfat <число>",
                "rpculinary.help.set", 2, false);
        RpCoreAPI.registerCommand(m, "/rpculinary reset <игроки>", "rpculinary.help.reset", 2, false);
        RpCoreAPI.registerCommand(m, "/rpculinary notes <группа> [игроки]", "rpculinary.help.notes", 2, false);
        RpCoreAPI.registerCommand(m, "/rpculinary audit [мод]", "rpculinary.help.audit", 2, true);
    }

    /** Время RP: {@code /rptime sleep|skip} — прокрутить голод, воду и вес (кроме «только раны»). */
    public static void onTimeSkip(RpTimeSkipEvent e) {
        if (e.food() && e.getEntity() instanceof ServerPlayer sp) advance(sp, e.hours(), e.sleeping());
    }

    /** Прокрутка голода и жажды на {@code hours} часов (без обмороков); во сне обмен веществ медленнее. */
    public static void advance(ServerPlayer sp, double hours, boolean sleeping) {
        NutritionState m = CulinaryService.state(sp);
        if (m == null || hours <= 0) return;
        Settings s = Settings.get();
        int mode = CulinaryService.thirstMode(sp, s);
        Metabolism.Input base = new Metabolism.Input();
        base.sleeping = sleeping;
        base.ownWater = mode == 1;
        base.metabolism = CulinaryService.metabolism(sp, s);
        if (mode == 2) Lso.addWater(sp, -s.waterLossPerHour * s.nutrientTimeScale() * hours);
        Metabolism.advance(m, hours, base, s);
        changed(sp);
    }

    private static int status(CommandSourceStack src, ServerPlayer sp) throws CommandSyntaxException {
        NutritionState m = CulinaryService.state(sp);
        if (m == null) return 0;
        Settings s = Settings.get();
        double hyd = CulinaryService.hydration(sp, m, s);
        src.sendSuccess(() -> Component.translatable("rpculinary.cmd.status_title", sp.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
        line(src, "rpculinary.cmd.status_food", pct(Metabolism.satiety(m, s)), Math.round(m.energy), Math.round(m.stomach),
                Metabolism.hungerStage(m, s), Math.round(Metabolism.normalKcalPerHour(m, s)));
        line(src, "rpculinary.cmd.status_water", pct(hyd), CulinaryService.thirstMode(sp, s) == 2 ? "LSO" : "",
                Metabolism.thirstStage(hyd, s));
        line(src, "rpculinary.cmd.status_nutrients", Math.round(m.nutrients[0]), Math.round(m.nutrients[1]), Math.round(m.nutrients[2]),
                Math.round(m.nutrients[3]), Nutrients.fedUpMask(m, s), Nutrients.poorAppetite(m, s) ? "+" : "-");
        line(src, "rpculinary.cmd.status_body", fmt(m.weightKg()), fmt(m.fatPercent()), fmt(m.leanKg), Math.round(m.recentKcal));
        if (m.starvingSeconds > 0 || m.dehydratedSeconds > 0)
            line(src, "rpculinary.cmd.status_starving", Math.round(m.starvingSeconds / 60), Math.round(m.dehydratedSeconds / 60));
        if (m.ivSeconds > 0) line(src, "rpculinary.cmd.status_iv", Math.round(m.ivKcal), Math.round(m.ivSeconds / 60));
        return 1;
    }

    private static void line(CommandSourceStack src, String key, Object... args) {
        src.sendSuccess(() -> Component.translatable(key, args).withStyle(ChatFormatting.GRAY), false);
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static void changed(ServerPlayer sp) {
        NutritionData d = NutritionData.get(sp);
        if (d != null) CulinaryService.sync(sp, d, true);
    }
}
