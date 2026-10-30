package faygolover.rpculinary.mixin;

import faygolover.rpculinary.core.Weights;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Стак ингредиентов по весу (таблица {@link Weights}); крупный кусок с весом в NBT — 1.
 * <p>У Кулинарии абсолютный приоритет (решение Alex 30.10): у предмета из таблицы весов стак всегда её, и OneStack
 * (он задаёт стак в начале метода и сразу выходит, в сборке — почти всему 16) его не перебивает. Поэтому считаем
 * первыми (приоритет миксина ниже обычного).
 */
@Mixin(value = ItemStack.class, priority = 500)
public abstract class ItemStackMixin {
    @Inject(method = "getMaxStackSize", at = @At("HEAD"), cancellable = true)
    private void rpculinary$weightStack(CallbackInfoReturnable<Integer> cir) {
        int ours = Weights.maxStack((ItemStack) (Object) this);
        if (ours > 0) cir.setReturnValue(ours);
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
