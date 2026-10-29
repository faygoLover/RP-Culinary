package faygolover.rpculinary.server;

import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.RpIds;
import faygolover.rpculinary.RpCulinary;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * Техники по умению (решения Alex 29.10.2026): без перка — «нельзя».
 * <ul>
 *   <li>«Винокур» ({@code rpculinary:vintner}) — самогон, пиво, вино: станции из тега блоков {@code rpculinary:technique/vintner}
 *   (пивоварни и бочки Brewery, бочка брожения Vinery) без умения не открываются. Пресс Vinery — нет: на нём и соки.</li>
 *   <li>«Заготовщик» ({@code rpculinary:preserver}) — консервы, копчение, засолка: предмет из тега
 *   {@code rpculinary:technique/preserver}, только что сделанный игроком без умения, пропадает («заготовка не вышла»).
 *   Консервы из лута не трогаются: проверка — только у того, что игрок сделал у станции.</li>
 * </ul>
 * Без RP Perks (навык не ведёт никто) — можно всем, как и знания экзотики. ГМ в творческом — тоже.
 */
public final class Techniques {
    private Techniques() {}

    public static final ResourceLocation VINTNER = new ResourceLocation(RpCulinary.MODID, "vintner"),
            PRESERVER = new ResourceLocation(RpCulinary.MODID, "preserver");
    static final TagKey<Block> VINTNER_STATIONS = BlockTags.create(new ResourceLocation(RpCulinary.MODID, "technique/vintner"));
    static final TagKey<Item> PRESERVES = ItemTags.create(new ResourceLocation(RpCulinary.MODID, "technique/preserver"));

    public static boolean allowed(Player p, ResourceLocation trait) {
        return p.isCreative() || !RpCoreAPI.isSkillManaged(RpIds.COOKING) || RpCoreAPI.hasTrait(p, trait);
    }

    /** Станция винокура без умения — не открывается. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !e.getLevel().getBlockState(e.getPos()).is(VINTNER_STATIONS)) return;
        if (allowed(sp, VINTNER)) return;
        sp.displayClientMessage(Component.translatable("rpculinary.msg.need_vintner").withStyle(ChatFormatting.GRAY), true);
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
    }

    /**
     * Только что сделанная заготовка без умения «Заготовщик»: убрать {@code n} непомеченных штук и сказать игроку.
     * true — заготовка не вышла (качество не ставить).
     */
    public static boolean spoilPreserves(ServerPlayer sp, Item item, int n) {
        if (n <= 0 || !new ItemStack(item).is(PRESERVES) || allowed(sp, PRESERVER)) return false;
        Inventory inv = sp.getInventory();
        ItemStack carried = sp.containerMenu.getCarried();
        if (carried.is(item) && CookingService.quality(carried) < 0) {
            int take = Math.min(n, carried.getCount());
            carried.shrink(take);
            n -= take;
        }
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.is(item) || CookingService.quality(st) >= 0) continue;
            int take = Math.min(n, st.getCount());
            st.shrink(take);
            n -= take;
        }
        sp.containerMenu.broadcastChanges();
        sp.displayClientMessage(Component.translatable("rpculinary.msg.need_preserver").withStyle(ChatFormatting.GRAY), true);
        return true;
    }
}
