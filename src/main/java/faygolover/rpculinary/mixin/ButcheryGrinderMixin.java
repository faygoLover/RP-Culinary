package faygolover.rpculinary.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Мясорубка Butchery — только колбаски (решения Alex 29.10): фарш мелет мясорубка F&C, а колбаски делаются из фарша + кишки
 * (+ кровь — кровяная), без обрезков и не из сырой свинины. Слоты Butchery: 0 — вход, 1 — кишки, 2 — насадка, 3 — кровь, 4 — выход.
 * Её рецепты зашиты в процедуру MCreator — перехватываем её: с насадкой и фаршем на входе делаем связку сами (4 фарша + кишка,
 * состав и облик — от фарша), прочий мясной помол отменяем. Butchery нет — миксин молча не применяется.
 */
@Pseudo
@Mixin(targets = "net.mcreator.butchery.procedures.MeatgrinderrecipeprocedureProcedure", remap = false)
public abstract class ButcheryGrinderMixin {
    private static final int RPCULINARY$MINCE_PER_BUNDLE = 4, RPCULINARY$TICKS = 60;

    @Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDD)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void rpculinary$sausages(LevelAccessor world, double x, double y, double z, CallbackInfo ci) {
        BlockEntity be = world.getBlockEntity(BlockPos.containing(x, y, z));
        if (be == null) return;
        var cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (!(cap instanceof IItemHandlerModifiable h) || h.getSlots() < 5) return;
        ItemStack input = h.getStackInSlot(0);
        boolean attachment = rpculinary$is(h.getStackInSlot(2), "butchery:sausage_attachment");
        boolean mince = input.is(ItemTags.create(new ResourceLocation("rpculinary", "mince")));
        boolean meat = mince || input.is(ItemTags.create(new ResourceLocation("forge", "raw_beef"))) || input.is(ItemTags.create(new ResourceLocation("forge", "raw_mutton")))
                || input.is(ItemTags.create(new ResourceLocation("forge", "raw_pork"))) || input.is(ItemTags.create(new ResourceLocation("c", "raw_pork")))
                || input.is(ItemTags.create(new ResourceLocation("forge", "raw_meat")));
        if (!meat) return;  // кости, отходы и прочее — как в Butchery
        ci.cancel();
        if (!attachment || !mince || world.isClientSide()) return;
        ItemStack guts = h.getStackInSlot(1), blood = h.getStackInSlot(3), out = h.getStackInSlot(4);
        if (input.getCount() < RPCULINARY$MINCE_PER_BUNDLE || !rpculinary$is(guts, "butchery:intestines")) return;
        boolean bloody = rpculinary$is(blood, "butchery:bottle_of_blood") || blood.is(ItemTags.create(new ResourceLocation("forge", "blood_bottle")));
        ItemStack bundle = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("butchery", bloody ? "raw_blood_sausage" : "raw_sausage")));
        faygolover.rpculinary.core.Source.copy(input, bundle);
        faygolover.rpculinary.core.MeatFood.carryFlags(java.util.List.of(input), bundle);
        if (!out.isEmpty() && (!ItemStack.isSameItemSameTags(out, bundle) || out.getCount() >= out.getMaxStackSize())) return;
        var pd = be.getPersistentData();
        int t = pd.getInt("rpculinary:grind") + 1;
        if (t < RPCULINARY$TICKS) {
            pd.putInt("rpculinary:grind", t);
            return;
        }
        pd.putInt("rpculinary:grind", 0);
        input.shrink(RPCULINARY$MINCE_PER_BUNDLE);
        h.setStackInSlot(0, input);
        guts.shrink(1);
        h.setStackInSlot(1, guts);
        if (bloody) {
            blood.shrink(1);
            h.setStackInSlot(3, blood);
        }
        if (out.isEmpty()) h.setStackInSlot(4, bundle);
        else {
            out.grow(1);
            h.setStackInSlot(4, out);
        }
        be.setChanged();
    }

    private static boolean rpculinary$is(ItemStack st, String id) {
        return !st.isEmpty() && id.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(st.getItem())));
    }
}
