package fr.luuuccasss.poverty.mixin;

import fr.luuuccasss.poverty.Indebted;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements Indebted {
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

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void poverty$write(ValueOutput output, CallbackInfo ci) {
        ValueOutput account = output.child("Poverty");
        account.putLong("Debt", poverty$debt);
        account.putLong("Arrears", poverty$arrears);
        account.putInt("UnpaidDays", poverty$unpaidDays);
        account.putLong("LastDay", poverty$lastDay);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void poverty$read(ValueInput input, CallbackInfo ci) {
        input.child("Poverty").ifPresent(account -> {
            poverty$debt = account.getLongOr("Debt", 0L);
            poverty$arrears = account.getLongOr("Arrears", 0L);
            poverty$unpaidDays = account.getIntOr("UnpaidDays", 0);
            poverty$lastDay = account.getLongOr("LastDay", -1L);
        });
    }

    /** La dette ne meurt pas avec toi. */
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void poverty$restoreFrom(ServerPlayer old, boolean alive, CallbackInfo ci) {
        Indebted from = (Indebted) old;
        poverty$debt = from.poverty$getDebt();
        poverty$arrears = from.poverty$getArrears();
        poverty$unpaidDays = from.poverty$getUnpaidDays();
        poverty$lastDay = from.poverty$getLastDay();
    }
}
