package fr.luuuccasss.poverty.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.renderer.entity.ZombieVillagerRenderer;

public class PovertyClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof ZombieVillagerRenderer zombieVillager) {
                helper.register(new BailiffLayer(zombieVillager));
            }
        });
    }
}
