package faygolover.rpculinary.network;

import faygolover.rpculinary.core.Cookbook;
import faygolover.rpculinary.item.CookbookItems;
import faygolover.rpculinary.server.CookingService;
import faygolover.rpculinary.server.Techniques;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Переписать рецепт из поваренной книги в личную (клиент → сервер): {@code begin} — начал, {@code !begin} — закончил. Сервер
 * проверяет, что прошло нужное время ({@link Cookbook#copyTicks}), книга раздела и личная книга при себе, навык не ниже и
 * есть перк раздела (заготовки, винокурня), — и только тогда пишет рецепт в личную книгу.
 */
public record CookbookPacket(boolean begin, String section, String recipe, int ingredients) {
    private static final Map<UUID, Object[]> STARTED = new HashMap<>();

    public static void encode(CookbookPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.begin);
        buf.writeUtf(p.section, 64);
        buf.writeUtf(p.recipe, 256);
        buf.writeVarInt(p.ingredients);
    }

    public static CookbookPacket decode(FriendlyByteBuf buf) {
        return new CookbookPacket(buf.readBoolean(), buf.readUtf(64), buf.readUtf(256), buf.readVarInt());
    }

    public static void handle(CookbookPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (sp != null) serve(sp, p);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void serve(ServerPlayer sp, CookbookPacket p) {
        int now = sp.server.getTickCount();
        String fail = check(sp, p);
        if (fail != null) {
            STARTED.remove(sp.getUUID());
            sp.displayClientMessage(Component.translatable("rpculinary.cookbook.fail." + fail).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (p.begin) {
            STARTED.put(sp.getUUID(), new Object[]{p.recipe, now});
            return;
        }
        Object[] st = STARTED.remove(sp.getUUID());
        int ings = sp.server.getRecipeManager().byKey(new ResourceLocation(p.recipe))
                .map(r -> (int) r.getIngredients().stream().filter(i -> !i.isEmpty()).count()).orElse(1);
        int need = Cookbook.copyTicks(ings) - 10;  // время — по рецепту на сервере; небольшой запас на задержку сети
        if (st == null || !p.recipe.equals(st[0]) || now - (int) st[1] < need) return;
        ItemStack notebook = notebook(sp);
        if (Cookbook.write(notebook, p.recipe)) {
            sp.containerMenu.broadcastChanges();
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
            sp.displayClientMessage(Component.translatable("rpculinary.cookbook.copied").withStyle(ChatFormatting.AQUA), true);
        }
    }

    /** Почему нельзя (ключ сообщения) или null. */
    private static String check(ServerPlayer sp, CookbookPacket p) {
        int skill = Cookbook.skill(p.section, p.recipe);
        if (skill < 0 || !hasBook(sp, p.section)) return "no_book";
        if (notebook(sp).isEmpty()) return "no_notebook";
        if (CookingService.level(sp) < skill) return "skill";
        Cookbook.Book b = Cookbook.book(p.section);
        if (b.perk() != null && !Techniques.allowed(sp, new ResourceLocation(b.perk()))) return "perk";
        return null;
    }

    private static boolean hasBook(ServerPlayer sp, String section) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(CookbookItems.COOKBOOK.get()) && section.equals(Cookbook.section(st))) return true;
        }
        return false;
    }

    private static ItemStack notebook(ServerPlayer sp) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(CookbookItems.NOTEBOOK.get())) return st;
        }
        return ItemStack.EMPTY;
    }
}
