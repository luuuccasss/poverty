package fr.luuuccasss.poverty.mixin;

import fr.luuuccasss.poverty.client.BailiffLayer;
import fr.luuuccasss.poverty.client.BailiffRenderState;
import net.minecraft.client.renderer.entity.ZombieVillagerRenderer;
import net.minecraft.client.renderer.entity.state.ZombieVillagerRenderState;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZombieVillagerRenderer.class)
public abstract class ZombieVillagerRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/monster/zombie/ZombieVillager;Lnet/minecraft/client/renderer/entity/state/ZombieVillagerRenderState;F)V",
        at = @At("TAIL"))
    private void poverty$extractBailiff(ZombieVillager entity, ZombieVillagerRenderState state, float partialTick, CallbackInfo ci) {
        ((BailiffRenderState) state).poverty$setBailiff(BailiffLayer.isBailiff(entity));
    }
}
