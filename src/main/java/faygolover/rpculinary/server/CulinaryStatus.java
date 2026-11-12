package faygolover.rpculinary.server;

import faygolover.rpcore.api.StatusLines;
import faygolover.rpculinary.core.Metabolism;
import faygolover.rpculinary.core.NutritionState;
import faygolover.rpculinary.core.Settings;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Жалобы на питание для осмотра RP Medicine: врач слышит то же, что пациент видит у себя в окне «Питание» — голод
 * и каждый нутриент не в норме, словами. Что именно не так (мало или много чего) — Диетолог, это добавляет
 * RP Medicine по запасам, которые берёт у мода питания. Себе и ГМу строки не нужны: у них окно и команда.
 */
public final class CulinaryStatus implements StatusLines.Source {
    public static final CulinaryStatus INSTANCE = new CulinaryStatus();

    @Override
    public void lines(Player target, StatusLines.Viewer viewer, List<StatusLines.Line> out) {
        if (viewer != StatusLines.Viewer.OTHER) return;
        NutritionState m = CulinaryService.state(target);
        if (m == null) return;
        Settings s = Settings.get();
        if (s.hungerEnabled) {
            int stage = Metabolism.hungerStage(m, s);
            if (stage > 0) out.add(new StatusLines.Line("rpculinary.exam.hunger_" + stage));
        }
        if (!s.nutrientsEnabled) return;
        for (int i = 0; i < NutritionState.COUNT; i++) {
            int z = NutritionState.zone(m.nutrients[i], s.nutrientLow, s.balancedMin, s.balancedMax, s.nutrientHigh);
            if (z == 2 || i == NutritionState.VITAMINS && z > 2) continue;
            out.add(new StatusLines.Line("rpculinary.exam." + NutritionState.NUTRIENTS[i] + "_" + NutritionState.ZONES[z]));
        }
    }
}
