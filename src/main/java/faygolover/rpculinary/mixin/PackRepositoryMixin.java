package faygolover.rpculinary.mixin;

import com.google.common.collect.ImmutableList;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Встроенный пак RP Culinary ({@code builtin/rpculinary_food}) — всегда выше всех. «TOP» у пака действует только при первом
 * появлении: порядок паков сохраняется в мире, и мод, добавленный позже, встаёт выше и перекрывает разделку и рецепты.
 * Поэтому при каждой сборке списка переносим наш пак в конец (конец = высший приоритет).
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
    private static final String RPCULINARY$PACK = "builtin/rpculinary_food";

    @Inject(method = "rebuildSelected", at = @At("RETURN"), cancellable = true)
    private void rpculinary$packOnTop(Collection<String> ids, CallbackInfoReturnable<List<Pack>> cir) {
        List<Pack> list = cir.getReturnValue();
        int i = -1;
        for (int k = 0; k < list.size(); k++) if (RPCULINARY$PACK.equals(list.get(k).getId())) i = k;
        if (i < 0 || i == list.size() - 1) return;
        List<Pack> moved = new ArrayList<>(list);
        moved.add(moved.remove(i));
        cir.setReturnValue(ImmutableList.copyOf(moved));
    }
}
