package fr.luuuccasss.poverty.mixin;

import fr.luuuccasss.poverty.Indebted;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin implements Indebted {
    @Unique private long poverty$debt;
    @Unique private long poverty$arrears;
    @Unique private int poverty$unpaidDays;
    @Unique private long poverty$lastDay = -1;

    @Override
    public long poverty$getDebt() {
        return poverty$debt;
    }

    @Override
    public void poverty$setDebt(long debt) {
        poverty$debt = Math.max(0, debt);
        poverty$arrears = Math.min(poverty$arrears, poverty$debt);
    }

    @Override
    public long poverty$getArrears() {
        return poverty$arrears;
    }

    @Override
    public void poverty$setArrears(long arrears) {
        poverty$arrears = Math.max(0, Math.min(arrears, poverty$debt));
        if (poverty$arrears == 0) poverty$unpaidDays = 0;
    }

    @Override
    public int poverty$getUnpaidDays() {
        return poverty$unpaidDays;
    }

    @Override
    public void poverty$setUnpaidDays(int days) {
        poverty$unpaidDays = Math.max(0, days);
    }

    @Override
    public long poverty$getLastDay() {
        return poverty$lastDay;
    }

    @Override
    public void poverty$setLastDay(long day) {
        poverty$lastDay = day;
    }

    @Inject(method = "writeCustomData", at = @At("TAIL"))
    private void poverty$write(WriteView view, CallbackInfo ci) {
        WriteView account = view.get("Poverty");
        account.putLong("Debt", poverty$debt);
        account.putLong("Arrears", poverty$arrears);
        account.putInt("UnpaidDays", poverty$unpaidDays);
        account.putLong("LastDay", poverty$lastDay);
    }

    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void poverty$read(ReadView view, CallbackInfo ci) {
        view.getOptionalReadView("Poverty").ifPresent(account -> {
            poverty$debt = account.getLong("Debt", 0L);
            poverty$arrears = account.getLong("Arrears", 0L);
            poverty$unpaidDays = account.getInt("UnpaidDays", 0);
            poverty$lastDay = account.getLong("LastDay", -1L);
        });
    }

    /** La dette ne meurt pas avec toi. */
    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void poverty$copyFrom(ServerPlayerEntity old, boolean alive, CallbackInfo ci) {
        Indebted from = (Indebted) old;
        poverty$debt = from.poverty$getDebt();
        poverty$arrears = from.poverty$getArrears();
        poverty$unpaidDays = from.poverty$getUnpaidDays();
        poverty$lastDay = from.poverty$getLastDay();
    }
}
