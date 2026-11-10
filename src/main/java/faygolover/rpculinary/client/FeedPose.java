package faygolover.rpculinary.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.client.event.RenderPlayerEvent;

import java.util.HashSet;
import java.util.Set;

/** Кормит другого: ведущая рука с едой вытянута вперёд. */
public final class FeedPose {
    private FeedPose() {}

    private static final Set<Integer> FEEDING = new HashSet<>();
    private static HumanoidModel.ArmPose pose;

    /** Позу создаём при запуске клиента: позже HumanoidModel её уже не примет. */
    public static void init() {
        pose = HumanoidModel.ArmPose.create("RPCULINARY_FEED", false, (model, entity, arm) -> {
            ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
            part.xRot = -1.45f + model.head.xRot * 0.5f;
            part.yRot = (arm == HumanoidArm.RIGHT ? -0.2f : 0.2f) + model.head.yRot * 0.5f;
            part.zRot = 0f;
        });
    }

    public static void set(int entity, boolean on) {
        if (on) FEEDING.add(entity);
        else FEEDING.remove(entity);
    }

    public static void onRenderPre(RenderPlayerEvent.Pre e) {
        if (pose == null || !FEEDING.contains(e.getEntity().getId())) return;
        PlayerModel<?> model = e.getRenderer().getModel();
        if (e.getEntity().getMainArm() == HumanoidArm.RIGHT) model.rightArmPose = pose;
        else model.leftArmPose = pose;
    }
}
