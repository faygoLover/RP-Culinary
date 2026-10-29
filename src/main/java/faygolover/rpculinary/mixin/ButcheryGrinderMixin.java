package faygolover.rpculinary.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Мясорубка Butchery — только колбасы и сосиски (решение Alex 29.10): фарш мелет мясорубка F&C. Без насадки для колбас
 * мясорубка Butchery говядину и баранину не мелет (её рецепты зашиты в процедуру MCreator — перехватываем её вход).
 * Вид мяса (пометка {@code rpculinary:source}) с входа переходит на сосиски. Butchery нет — миксин молча не применяется.
 */
@Pseudo
@Mixin(targets = "net.mcreator.butchery.procedures.MeatgrinderrecipeprocedureProcedure", remap = false)
public abstract class ButcheryGrinderMixin {
    private static final ResourceLocation RPCULINARY$ATTACHMENT = new ResourceLocation("butchery", "sausage_attachment");

    @Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDD)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void rpculinary$noMince(LevelAccessor world, double x, double y, double z, CallbackInfo ci) {
        BlockEntity be = world.getBlockEntity(BlockPos.containing(x, y, z));
        if (be == null) return;
        var h = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (h == null || h.getSlots() < 3) return;
        ItemStack input = h.getStackInSlot(0), tool = h.getStackInSlot(2);
        RPCULINARY$SOURCE.set(faygolover.rpculinary.core.Source.marked(input) ? faygolover.rpculinary.core.Source.list(input) : null);
        if (RPCULINARY$ATTACHMENT.equals(ForgeRegistries.ITEMS.getKey(tool.getItem()))) return;  // колбасы — можно
        if (input.is(ItemTags.create(new ResourceLocation("forge", "raw_beef"))) || input.is(ItemTags.create(new ResourceLocation("c", "raw_beef")))
                || input.is(ItemTags.create(new ResourceLocation("forge", "raw_mutton"))) || input.is(ItemTags.create(new ResourceLocation("c", "raw_mutton"))))
            ci.cancel();
    }

    private static final ThreadLocal<java.util.List<String>> RPCULINARY$SOURCE = new ThreadLocal<>();

    /** После шага мясорубки: новые сосиски без пометки — из того мяса, что было на входе. */
    @Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDD)V", at = @At("RETURN"), require = 0, remap = false)
    private static void rpculinary$source(LevelAccessor world, double x, double y, double z, CallbackInfo ci) {
        java.util.List<String> src = RPCULINARY$SOURCE.get();
        RPCULINARY$SOURCE.remove();
        if (src == null || src.isEmpty()) return;
        BlockEntity be = world.getBlockEntity(BlockPos.containing(x, y, z));
        var h = be == null ? null : be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (h == null) return;
        for (int i = 1; i < h.getSlots(); i++) {
            ItemStack st = h.getStackInSlot(i);
            String id = String.valueOf(ForgeRegistries.ITEMS.getKey(st.getItem()));
            if (id.contains("sausage") && !id.contains("attachment") && !faygolover.rpculinary.core.Source.marked(st))
                faygolover.rpculinary.core.Source.add(st, src);
        }
    }
}
