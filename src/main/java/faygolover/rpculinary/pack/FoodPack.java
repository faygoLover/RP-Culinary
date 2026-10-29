package faygolover.rpculinary.pack;

import faygolover.rpculinary.RpCulinary;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

import java.nio.file.Path;

/**
 * Встроенный датапак «поверх всех модов» ({@code resourcepacks/rpculinary_food}, собирается {@code tools/gen_food_pack.py}):
 * разделка туш Butchery, теги, дроп мобов, рецепты. Данные самого мода не гарантированно перекрывают чужие (порядок
 * загрузки модов), а пак выше всех — всегда (позицию держит {@link faygolover.rpculinary.mixin.PackRepositoryMixin}). Включён всегда.
 */
public final class FoodPack {
    private FoodPack() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(FoodPack::addPacks);
    }

    private static void addPacks(AddPackFindersEvent e) {
        if (e.getPackType() != PackType.SERVER_DATA) return;
        Path path = ModList.get().getModFileById(RpCulinary.MODID).getFile().findResource("resourcepacks/rpculinary_food");
        Pack pack = Pack.readMetaAndCreate("builtin/rpculinary_food", Component.literal("RP Culinary: разделка и рецепты"), true,
                id -> new PathPackResources(id, path, true), PackType.SERVER_DATA, Pack.Position.TOP, PackSource.BUILT_IN);
        if (pack != null) e.addRepositorySource(add -> add.accept(pack));
    }
}
