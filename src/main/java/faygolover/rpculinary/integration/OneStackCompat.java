package faygolover.rpculinary.integration;

import net.minecraft.world.item.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** OneStack (если стоит): стак предмета по его конфигу; 0 — не задан или мода нет. Без жёсткой зависимости. */
public final class OneStackCompat {
    private OneStackCompat() {}

    private static final MethodHandle GET;

    static {
        MethodHandle h = null;
        try {
            Class<?> c = Class.forName("net.jcraron.mc.onestack.api.MaxCountConfig");
            h = MethodHandles.publicLookup().findStatic(c, "getMaxCount", MethodType.methodType(int.class, ItemStack.class));
        } catch (Throwable ignored) {
        }
        GET = h;
    }

    public static int maxCount(ItemStack st) {
        if (GET == null) return 0;
        try {
            return (int) GET.invokeExact(st);
        } catch (Throwable t) {
            return 0;
        }
    }
}
