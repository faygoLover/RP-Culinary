package faygolover.rpculinary.item;

import faygolover.rpculinary.RpCulinary;
import faygolover.rpculinary.server.Exotic;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Журнал (охотника, грибника…) или записи исследований по группе экзотики — от ГМа (решения Alex 29.10.2026; обложка
 * по группе и описание вместо пояснений — 02.11): ПКМ —
 * изучить группу (служебный перк RP Perks). Книга не тратится и ставится на книжную полку (тег {@code minecraft:bookshelf_books});
 * для изучения нужен навык «Кулинарии» не ниже {@link Exotic#minSkill}. Группа — в NBT {@code group}.
 */
public class ResearchNotes extends Item {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RpCulinary.MODID);
    public static final RegistryObject<Item> NOTES = ITEMS.register("research_notes", () -> new ResearchNotes(
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final String GROUP = "group";

    public ResearchNotes(Properties p) {
        super(p);
    }

    /** Книга группы {@code g}. */
    public static ItemStack of(int g) {
        ItemStack st = new ItemStack(NOTES.get());
        st.getOrCreateTag().putString(GROUP, Exotic.GROUPS.get(g));
        return st;
    }

    public static int group(ItemStack st) {
        return st.getTag() == null ? -1 : Exotic.index(st.getTag().getString(GROUP));
    }

    public static Component groupName(int g) {
        return Component.translatable("rpculinary.exotic." + Exotic.GROUPS.get(g));
    }

    @Override
    public Component getName(ItemStack st) {
        int g = group(st);
        return g < 0 ? super.getName(st) : Component.translatable("item.rpculinary.research_notes." + Exotic.GROUPS.get(g));
    }

    /** Подсказка — короткое описание журнала (о чём, кто писал), без пояснений механики (D1, 02.11). */
    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        int g = group(st);
        if (g < 0) return;
        for (String line : Component.translatable("item.rpculinary.research_notes." + Exotic.GROUPS.get(g) + ".lore").getString().split("\n"))
            tip.add(Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(st);
        int g = group(st);
        if (!Exotic.learnable(g)) return InteractionResultHolder.fail(st);
        if (!Exotic.knows(sp, g) && faygolover.rpculinary.server.CookingService.level(sp) < Exotic.minSkill(g)) {
            sp.displayClientMessage(Component.translatable("rpculinary.msg.notes_too_hard", Exotic.minSkill(g)).withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(st);
        }
        int r = Exotic.learn(sp, g);
        if (r == 1) {
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
            sp.displayClientMessage(Component.translatable("rpculinary.msg.notes_learned", groupName(g)).withStyle(ChatFormatting.AQUA), true);
            return InteractionResultHolder.consume(st);
        }
        sp.displayClientMessage(Component.translatable(r == 0 ? "rpculinary.msg.notes_known" : "rpculinary.msg.notes_nobody",
                groupName(g)).withStyle(ChatFormatting.GRAY), true);
        return InteractionResultHolder.fail(st);
    }
}
