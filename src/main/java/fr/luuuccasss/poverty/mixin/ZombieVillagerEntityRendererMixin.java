package fr.luuuccasss.poverty.mixin;

import fr.luuuccasss.poverty.client.BailiffFeatureRenderer;
import fr.luuuccasss.poverty.client.BailiffRenderState;
import net.minecraft.client.render.entity.ZombieVillagerEntityRenderer;
import net.minecraft.client.render.entity.state.ZombieVillagerRenderState;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZombieVillagerEntityRenderer.class)
public abstract class ZombieVillagerEntityRendererMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/mob/ZombieVillagerEntity;Lnet/minecraft/client/render/entity/state/ZombieVillagerRenderState;F)V",
        at = @At("TAIL"))
    private void poverty$copyBailiff(ZombieVillagerEntity entity, ZombieVillagerRenderState state, float tickDelta, CallbackInfo ci) {
        ((BailiffRenderState) state).poverty$setBailiff(BailiffFeatureRenderer.isBailiff(entity));
    }
}
