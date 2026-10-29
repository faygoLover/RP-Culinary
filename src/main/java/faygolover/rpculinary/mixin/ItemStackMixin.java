package faygolover.rpculinary.mixin;

import faygolover.rpculinary.core.Weights;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Стак ингредиентов по весу (таблица {@link Weights}); крупный кусок с весом в NBT — 1. Только уменьшаем. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "getMaxStackSize", at = @At("RETURN"), cancellable = true)
    private void rpculinary$weightStack(CallbackInfoReturnable<Integer> cir) {
        int ours = Weights.maxStack((ItemStack) (Object) this);
        if (ours > 0 && ours < cir.getReturnValueI()) cir.setReturnValue(ours);
    }
}
