package fr.luuuccasss.poverty;

import fr.luuuccasss.poverty.mixin.MobAccessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;

public class Poverty implements ModInitializer {
    public static final String MOD_ID = "poverty";

    @Override
    public void onInitialize() {
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            for (ServerPlayer player : level.players()) {
                if ((level.getGameTime() + player.getId()) % 20 != 0) continue;
                Bank.tick(level, player);
                Bailiff.tick(level, player);
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> Bailiff.onLoad(entity));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ZombieVillager bailiff) || !bailiff.entityTags().contains(Bailiff.TAG)) return;
            ServerPlayer killer = source.getEntity() instanceof ServerPlayer p ? p : null;
            for (WrappedGoal goal : ((MobAccessor) bailiff).poverty$goals().getAvailableGoals()) {
                if (goal.getGoal() instanceof SeizeGoal seize) seize.onKilledBy((ServerLevel) bailiff.level(), killer);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Bailiff.forget(handler.getPlayer()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DebtCommand.register(dispatcher));
    }
}
