package faygolover.rpculinary.server;

import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.RpIds;
import faygolover.rpculinary.RpCulinary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Map;

/**
 * Группы экзотики (собраны заново 29.10.2026, ответы Alex в галерее; состав — {@code tools/groups_spec.py} →
 * {@code tools/gen_groups.py} → теги {@code rpculinary:exotic/<группа>}). Блюдо наследует группы от ингредиентов, как вид
 * еды ({@link NutritionTable#exoticMask}).
 * <p>
 * У части групп есть знание — служебный перк RP Perks (черта {@code rpculinary:exotic/<группа>}). Его даёт журнал (обычное
 * повседневное знание: охотник, грибник…) или записи исследований (иные миры, монстры) — книга не тратится, стоит на
 * полке, но для изучения нужен минимальный навык «Кулинарии» ({@link #minSkill}). Без знания блюдо выходит хуже и чаще
 * травит; у некоторых групп это и опасно (иглобрюх, дикие грибы, субпродукты).
 */
public final class Exotic {
    private Exotic() {}

    public static final List<String> GROUPS = List.of("game", "pets", "pet_food", "seafood", "fugu", "deep_sea", "insects", "offal",
            "mushrooms", "other_worlds", "monsters", "human", "spoiled", "radioactive", "anomalous", "sweets");
    /** Группы со знанием → навык «Кулинарии», нужный, чтобы его изучить. */
    private static final Map<String, Integer> SKILL = Map.of("game", 3, "seafood", 3, "fugu", 7, "deep_sea", 4, "insects", 2,
            "offal", 3, "mushrooms", 4, "other_worlds", 6, "monsters", 7);
    /** Знание — записи исследований, а не повседневный журнал. */
    private static final java.util.Set<String> RESEARCH = java.util.Set.of("other_worlds", "monsters");
    /** Черта «Каннибал» и «Аллергия на морепродукты» (перки RP Perks). */
    public static final ResourceLocation CANNIBAL = new ResourceLocation(RpCulinary.MODID, "cannibal"),
            SEAFOOD_ALLERGY = new ResourceLocation(RpCulinary.MODID, "seafood_allergy");

    @SuppressWarnings("unchecked")
    private static final TagKey<Item>[] TAGS = new TagKey[GROUPS.size()];
    private static final ResourceLocation[] KNOWLEDGE = new ResourceLocation[GROUPS.size()];

    static {
        for (int i = 0; i < GROUPS.size(); i++) {
            TAGS[i] = ItemTags.create(new ResourceLocation(RpCulinary.MODID, "exotic/" + GROUPS.get(i)));
            KNOWLEDGE[i] = new ResourceLocation(RpCulinary.MODID, "exotic/" + GROUPS.get(i));
        }
    }

    public static int index(String group) {
        return GROUPS.indexOf(group);
    }

    public static int bit(String group) {
        return 1 << index(group);
    }

    /** Есть ли у группы знание (журнал или записи). */
    public static boolean learnable(int g) {
        return g >= 0 && g < GROUPS.size() && SKILL.containsKey(GROUPS.get(g));
    }

    /** Навык «Кулинарии», нужный для изучения, или -1. */
    public static int minSkill(int g) {
        return learnable(g) ? SKILL.get(GROUPS.get(g)) : -1;
    }

    /** Записи исследований (иные миры, монстры), а не журнал. */
    public static boolean research(int g) {
        return g >= 0 && g < GROUPS.size() && RESEARCH.contains(GROUPS.get(g));
    }

    /** Группы, к которым предмет отнесён прямо (тегом), битовой маской. */
    public static int directMask(Item item) {
        var holder = item.builtInRegistryHolder();
        int mask = 0;
        for (int i = 0; i < TAGS.length; i++) if (holder.is(TAGS[i])) mask |= 1 << i;
        return mask;
    }

    /** Знает ли игрок группу. Без RP Perks (навык не ведёт никто) — знает всё, как уровень 10. */
    public static boolean knows(Player p, int g) {
        if (!learnable(g)) return true;
        if (!RpCoreAPI.isSkillManaged(RpIds.COOKING)) return true;
        return RpCoreAPI.hasTrait(p, KNOWLEDGE[g]);
    }

    public static boolean knows(Player p, String group) {
        return knows(p, index(group));
    }

    /** Группы из маски, которых повар не знает. */
    public static int unknownMask(Player p, int mask) {
        int out = 0;
        for (int i = 0; i < GROUPS.size(); i++) if ((mask & 1 << i) != 0 && !knows(p, i)) out |= 1 << i;
        return out;
    }

    /** Ест «Каннибал». */
    public static boolean cannibal(Player p) {
        return RpCoreAPI.hasTrait(p, CANNIBAL);
    }

    /**
     * Изучить группу: служебный перк через RP Perks (отражением — Culinary от Perks не зависит).
     * 1 — изучил, 0 — уже знал, −1 — некому выдать (нет RP Perks или нет такого знания).
     */
    public static int learn(ServerPlayer sp, int g) {
        if (!learnable(g)) return -1;
        try {
            Class<?> c = Class.forName("faygolover.rpperks.compat.CoreTraits");
            Object r = c.getMethod("grantKnowledge", ServerPlayer.class, ResourceLocation.class).invoke(null, sp, KNOWLEDGE[g]);
            return r instanceof Integer i ? i : -1;
        } catch (ReflectiveOperationException | LinkageError e) {
            return -1;
        }
    }
}
