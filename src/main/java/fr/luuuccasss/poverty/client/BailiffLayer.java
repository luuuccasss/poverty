package fr.luuuccasss.poverty.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.luuuccasss.poverty.Poverty;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.zombie.ZombieVillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ZombieVillagerRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;

/** Haut-de-forme et mallette de l'huissier, pour les joueurs qui ont le mod. */
public class BailiffLayer extends RenderLayer<ZombieVillagerRenderState, ZombieVillagerModel<ZombieVillagerRenderState>> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Poverty.MOD_ID, "textures/entity/huissier.png");
    private static final String NAME = "Huissier de justice";

    private final ModelPart hat;
    private final ModelPart briefcase;

    public BailiffLayer(RenderLayerParent<ZombieVillagerRenderState, ZombieVillagerModel<ZombieVillagerRenderState>> parent) {
        super(parent);
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition hatData = root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        hatData.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(0, 0).addBox(-6, -12, -6, 12, 1, 12), PartPose.ZERO);
        hatData.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0, 13).addBox(-4, -19, -4, 8, 7, 8), PartPose.ZERO);
        PartDefinition caseData = root.addOrReplaceChild("briefcase", CubeListBuilder.create(), PartPose.ZERO);
        caseData.addOrReplaceChild("body", CubeListBuilder.create().texOffs(32, 13).addBox(-1, 0, -5, 2, 7, 10), PartPose.ZERO);
        caseData.addOrReplaceChild("handle", CubeListBuilder.create().texOffs(48, 0).addBox(-0.5f, -2, -2, 1, 2, 4), PartPose.ZERO);
        ModelPart model = LayerDefinition.create(mesh, 64, 32).bakeRoot();
        hat = model.getChild("hat");
        briefcase = model.getChild("briefcase");
    }

    public static boolean isBailiff(ZombieVillager entity) {
        Component name = entity.getCustomName();
        return name != null && NAME.equals(name.getString());
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, ZombieVillagerRenderState state,
                       float yRot, float xRot) {
        if (!((BailiffRenderState) state).poverty$isBailiff() || state.isInvisible) return;
        RenderType type = RenderTypes.entityCutout(TEXTURE);
        int overlay = OverlayTexture.NO_OVERLAY;

        poseStack.pushPose();
        getParentModel().getHead().translateAndRotate(poseStack);
        collector.submitModelPart(hat, poseStack, type, light, overlay, null);
        poseStack.popPose();

        // mallette pendue à la main gauche
        poseStack.pushPose();
        getParentModel().translateToHand(state, HumanoidArm.LEFT, poseStack);
        poseStack.translate(1 / 16f, 11 / 16f, 0);
        collector.submitModelPart(briefcase, poseStack, type, light, overlay, null);
        poseStack.popPose();
    }
}
