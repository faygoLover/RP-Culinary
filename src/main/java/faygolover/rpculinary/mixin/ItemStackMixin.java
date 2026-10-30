package faygolover.rpculinary.mixin;

import faygolover.rpculinary.core.Weights;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Стак ингредиентов по весу (таблица {@link Weights}); крупный кусок с весом в NBT — 1. Только уменьшаем.
 * <p>OneStack задаёт стак в начале метода и сразу выходит (в сборке — почти всему 16), и проверка в конце до нас бы не
 * доходила. Поэтому считаем первыми (приоритет ниже обычного): берём стак OneStack, если он задан, иначе — предмета,
 * и ставим меньшее из него и своего (30.10).
 */
@Mixin(value = ItemStack.class, priority = 500)
public abstract class ItemStackMixin {
    @Inject(method = "getMaxStackSize", at = @At("HEAD"), cancellable = true)
    private void rpculinary$weightStack(CallbackInfoReturnable<Integer> cir) {
        ItemStack st = (ItemStack) (Object) this;
        int ours = Weights.maxStack(st);
        if (ours <= 0) return;
        int base = faygolover.rpculinary.integration.OneStackCompat.maxCount(st);
        if (base <= 0) base = st.getItem().getMaxStackSize(st);
        if (ours < base) cir.setReturnValue(ours);
    }

    /** Домашнее (смесь видов) и ливерный фарш — своё название формы (решения Alex 29.10). */
    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void rpculinary$homeName(CallbackInfoReturnable<net.minecraft.network.chat.Component> cir) {
        ItemStack st = (ItemStack) (Object) this;
        if (st.hasCustomHoverName() || faygolover.rpculinary.core.MeatFood.form(st) == null) return;
        String id = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(st.getItem())).replace(':', '.');
        if (st.getTag() != null && st.getTag().getBoolean(faygolover.rpculinary.core.MeatFood.OFFAL_FORM))
            cir.setReturnValue(net.minecraft.network.chat.Component.translatable("rpculinary.name.offal." + id));
        else if (faygolover.rpculinary.core.Source.home(st))
            cir.setReturnValue(net.minecraft.network.chat.Component.translatable("rpculinary.name.home." + id));
    }
}
