package fr.luuuccasss.poverty.mixin;

import fr.luuuccasss.poverty.client.BailiffRenderState;
import net.minecraft.client.renderer.entity.state.ZombieVillagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ZombieVillagerRenderState.class)
public abstract class ZombieVillagerRenderStateMixin implements BailiffRenderState {
    @Unique private boolean poverty$bailiff;

    @Override
    public boolean poverty$isBailiff() {
        return poverty$bailiff;
    }

    @Override
    public void poverty$setBailiff(boolean bailiff) {
        poverty$bailiff = bailiff;
    }
}
