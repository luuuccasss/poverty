package fr.luuuccasss.poverty.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.ZombieVillagerEntityRenderer;

public class PovertyClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof ZombieVillagerEntityRenderer zombieVillager) {
                helper.register(new BailiffFeatureRenderer(zombieVillager));
            }
        });
    }
}
