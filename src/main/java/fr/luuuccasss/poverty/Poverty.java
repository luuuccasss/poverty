package fr.luuuccasss.poverty;

import fr.luuuccasss.poverty.mixin.MobEntityAccessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class Poverty implements ModInitializer {
    public static final String MOD_ID = "poverty";

    @Override
    public void onInitialize() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            for (ServerPlayerEntity player : world.getPlayers()) {
                if ((world.getTime() + player.getId()) % 20 != 0) continue;
                Bank.tick(world, player);
                Bailiff.tick(world, player);
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> Bailiff.onLoad(entity));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ZombieVillagerEntity bailiff) || !bailiff.getCommandTags().contains(Bailiff.TAG)) return;
            ServerPlayerEntity killer = source.getAttacker() instanceof ServerPlayerEntity p ? p : null;
            for (PrioritizedGoal goal : ((MobEntityAccessor) bailiff).poverty$goals().getGoals()) {
                if (goal.getGoal() instanceof SeizeGoal seize) seize.onKilledBy((ServerWorld) bailiff.getWorld(), killer);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Bailiff.forget(handler.getPlayer()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DebtCommand.register(dispatcher));
    }
}
