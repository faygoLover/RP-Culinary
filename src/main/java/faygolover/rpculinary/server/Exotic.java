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

/**
 * Группы экзотической кухни (решения Alex 27.10.2026). Состав группы — тег предметов {@code rpculinary:exotic/<группа>};
 * блюдо наследует группы от ингредиентов, как вид еды ({@link NutritionTable#exoticMask}).
 * <p>
 * Знание группы — служебный перк RP Perks (черта {@code rpculinary:exotic/<группа>}), его даёт книга «Записи
 * исследований». Без знания блюдо выходит хуже (не выше «хорошего») и травит. У «Человеческого» книги нет: там
 * важен едок («Каннибал»), не повар.
 */
public final class Exotic {
    private Exotic() {}

    public static final List<String> GROUPS = List.of("underground", "candy", "deep_sea", "far_lands", "nether", "end",
            "pets", "monsters", "infected", "radioactive", "anomalous", "human");
    public static final int HUMAN = GROUPS.indexOf("human");
    /** Черта «Каннибал» (перк RP Perks). */
    public static final ResourceLocation CANNIBAL = new ResourceLocation(RpCulinary.MODID, "cannibal");

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

    /** Есть ли у группы книга-знание (у «Человеческого» — нет). */
    public static boolean learnable(int g) {
        return g >= 0 && g < GROUPS.size() && g != HUMAN;
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
