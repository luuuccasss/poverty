package fr.luuuccasss.poverty.client;

import fr.luuuccasss.poverty.Poverty;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.ZombieVillagerEntityModel;
import net.minecraft.client.render.entity.state.ZombieVillagerRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;

/** Haut-de-forme et mallette de l'huissier, pour les joueurs qui ont le mod. */
public class BailiffFeatureRenderer extends FeatureRenderer<ZombieVillagerRenderState, ZombieVillagerEntityModel<ZombieVillagerRenderState>> {
    private static final Identifier TEXTURE = Identifier.of(Poverty.MOD_ID, "textures/entity/huissier.png");
    private static final String NAME = "Huissier de justice";

    private final ModelPart hat;
    private final ModelPart briefcase;

    public BailiffFeatureRenderer(FeatureRendererContext<ZombieVillagerRenderState, ZombieVillagerEntityModel<ZombieVillagerRenderState>> context) {
        super(context);
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartData hatData = root.addChild("hat", ModelPartBuilder.create(), ModelTransform.NONE);
        hatData.addChild("brim", ModelPartBuilder.create().uv(0, 0).cuboid(-6, -12, -6, 12, 1, 12), ModelTransform.NONE);
        hatData.addChild("crown", ModelPartBuilder.create().uv(0, 13).cuboid(-4, -19, -4, 8, 7, 8), ModelTransform.NONE);
        ModelPartData caseData = root.addChild("briefcase", ModelPartBuilder.create(), ModelTransform.NONE);
        caseData.addChild("body", ModelPartBuilder.create().uv(32, 13).cuboid(-1, 0, -5, 2, 7, 10), ModelTransform.NONE);
        caseData.addChild("handle", ModelPartBuilder.create().uv(48, 0).cuboid(-0.5f, -2, -2, 1, 2, 4), ModelTransform.NONE);
        ModelPart model = TexturedModelData.of(data, 64, 32).createModel();
        hat = model.getChild("hat");
        briefcase = model.getChild("briefcase");
    }

    public static boolean isBailiff(ZombieVillagerEntity entity) {
        Text name = entity.getCustomName();
        return name != null && NAME.equals(name.getString());
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, ZombieVillagerRenderState state,
                       float limbAngle, float limbDistance) {
        if (!((BailiffRenderState) state).poverty$isBailiff() || state.invisible) return;
        RenderLayer layer = RenderLayers.entityCutoutNoCull(TEXTURE);
        int overlay = OverlayTexture.DEFAULT_UV;

        matrices.push();
        getContextModel().getHead().applyTransform(matrices);
        queue.submitModelPart(hat, matrices, layer, light, overlay, null);
        matrices.pop();

        // mallette pendue à la main gauche
        matrices.push();
        getContextModel().setArmAngle(state, Arm.LEFT, matrices);
        matrices.translate(1 / 16f, 11 / 16f, 0);
        queue.submitModelPart(briefcase, matrices, layer, light, overlay, null);
        matrices.pop();
    }
}
