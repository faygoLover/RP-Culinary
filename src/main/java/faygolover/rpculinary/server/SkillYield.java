package faygolover.rpculinary.server;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import faygolover.rpculinary.RpCulinary;
import faygolover.rpculinary.core.Weights;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Функция добычи {@code rpculinary:skill_yield} (блок 3 переделки): на мясе и органах в таблицах разделки туш. Пока идёт
 * разрез ({@link Butchering#current()}), каждая штука с шансом по отставанию навыка от порога туши уходит в потери —
 * их вес {@link Butchering} потом отдаёт обрезками. Вне разреза (команда, чужой мод) ничего не меняет.
 */
public class SkillYield extends LootItemConditionalFunction {
    public static final DeferredRegister<LootItemFunctionType> TYPES = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, RpCulinary.MODID);
    public static final RegistryObject<LootItemFunctionType> TYPE = TYPES.register("skill_yield", () -> new LootItemFunctionType(new Serializer()));

    SkillYield(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public LootItemFunctionType getType() {
        return TYPE.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext ctx) {
        Butchering.Cut cut = Butchering.current();
        if (cut == null || stack.isEmpty()) return stack;
        int grams = Math.max(1, Weights.grams(stack));
        int kept = 0;
        for (int i = 0; i < stack.getCount(); i++) {
            if (ctx.getRandom().nextDouble() < cut.lossShare()) cut.lostGrams += grams;
            else kept++;
        }
        cut.keptGrams += (double) kept * grams;
        stack.setCount(kept);
        return kept > 0 ? stack : ItemStack.EMPTY;
    }

    public static class Serializer extends LootItemConditionalFunction.Serializer<SkillYield> {
        @Override
        public SkillYield deserialize(JsonObject json, JsonDeserializationContext ctx, LootItemCondition[] conditions) {
            return new SkillYield(conditions);
        }
    }
}
