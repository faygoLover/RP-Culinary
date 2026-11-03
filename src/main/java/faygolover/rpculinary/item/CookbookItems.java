package faygolover.rpculinary.item;

import faygolover.rpculinary.core.Cookbook;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Поваренная книга раздела (выдаёт ГМ, раскладывают как лут) и личная книга рецептов повара (D3, решения Алекса 03.11).
 * ПКМ — открыть: в поваренной книге рецепты раздела и кнопка «Переписать», в личной — база и переписанное.
 */
public final class CookbookItems {
    private CookbookItems() {}

    public static final RegistryObject<Item> COOKBOOK = ResearchNotes.ITEMS.register("cookbook", () -> new CookbookItem(
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> NOTEBOOK = ResearchNotes.ITEMS.register("recipe_notebook", () -> new NotebookItem(
            new Item.Properties().stacksTo(1)));

    /** Вызвать при создании мода, чтобы предметы попали в регистр. */
    public static void init() {}

    public static ItemStack book(String section) {
        ItemStack st = new ItemStack(COOKBOOK.get());
        st.getOrCreateTag().putString(Cookbook.SECTION, section);
        return st;
    }

    public static class CookbookItem extends Item {
        public CookbookItem(Properties p) {
            super(p);
        }

        @Override
        public Component getName(ItemStack st) {
            String s = Cookbook.section(st);
            return s.isEmpty() ? super.getName(st) : Component.translatable("item.rpculinary.cookbook." + s);
        }

        @Override
        public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
            String s = Cookbook.section(st);
            if (s.isEmpty()) return;
            for (String line : Component.translatable("item.rpculinary.cookbook." + s + ".lore").getString().split("\n"))
                tip.add(Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack st = player.getItemInHand(hand);
            if (level.isClientSide && !Cookbook.section(st).isEmpty())
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpculinary.client.CookbookScreen.openBook(Cookbook.section(st)));
            return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
        }
    }

    public static class NotebookItem extends Item {
        public NotebookItem(Properties p) {
            super(p);
        }

        @Override
        public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
            tip.add(Component.translatable("item.rpculinary.recipe_notebook.lore", Cookbook.learned(st).size())
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack st = player.getItemInHand(hand);
            if (level.isClientSide)
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpculinary.client.CookbookScreen.openNotebook(st));
            return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
        }
    }
}
