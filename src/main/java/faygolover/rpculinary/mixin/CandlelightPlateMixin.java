package faygolover.rpculinary.mixin;

import faygolover.rpculinary.server.CulinaryEating;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import faygolover.rpculinary.server.CulinaryService;

/**
 * Тарелка Let's Do Candlelight: еду с неё мод скармливает напрямую в ванильную сытость (×1,3) и не сообщает, что съедено.
 * Берём снятый с тарелки предмет и считаем его как обычную порцию, ванильный прирост уже не учитывается. Candlelight нет —
 * миксин молча не применяется.
 */
@Pseudo
@Mixin(targets = "net.satisfy.candlelight.core.block.TableSetBlock", remap = false)
public abstract class CandlelightPlateMixin {
    @ModifyVariable(method = "remove(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/satisfy/candlelight/core/block/entity/StorageBlockEntity;I)V",
            at = @At("STORE"), ordinal = 0, require = 0, remap = false)
    private ItemStack rpculinary$ate(ItemStack stack) {
        if (stack.isEdible()) CulinaryEating.ateFromPlate(stack);
        return stack;
    }
}
