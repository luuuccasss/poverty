package fr.luuuccasss.poverty.mixin;

import fr.luuuccasss.poverty.Bank;
import fr.luuuccasss.poverty.Indebted;
import fr.luuuccasss.poverty.Money;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin {
    /**
     * Accroupi avec des émeraudes en main : on rembourse sa dette au villageois.
     * Fiché comme mauvais payeur : plus personne ne commerce avec toi.
     */
    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void poverty$interact(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || player.isCreative()) return;
        VillagerEntity self = (VillagerEntity) (Object) this;
        if (self.isBaby()) return;
        Indebted account = (Indebted) player;
        ItemStack held = player.getStackInHand(hand);
        if (player.isSneaking() && Money.value(held) > 0 && account.poverty$getDebt() > 0) {
            long amount = Math.min(Money.value(held), account.poverty$getDebt());
            long paid = Money.take(serverPlayer, amount);
            Bank.repay((ServerWorld) self.getWorld(), serverPlayer, paid);
            self.getWorld().playSound(null, self.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1f, 1f);
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }
        if (account.poverty$getUnpaidDays() >= Bank.BLACKLIST_AFTER_DAYS) {
            self.setHeadRollingTimeLeft(40);
            self.getWorld().playSound(null, self.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 1f, 0.8f);
            player.sendMessage(Text.literal("« Tu es fiché. Rembourse d'abord tes " + account.poverty$getArrears()
                + " émeraudes d'impayés. »").formatted(Formatting.RED), true);
            cir.setReturnValue(ActionResult.SUCCESS);
        }
    }

    /** Les mauvais payeurs paient plus cher. */
    @Inject(method = "prepareOffersFor", at = @At("TAIL"))
    private void poverty$badPayerPrices(PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof Indebted account)) return;
        double malus = Bank.priceMalus(account);
        if (malus <= 0) return;
        for (TradeOffer offer : ((VillagerEntity) (Object) this).getOffers()) {
            int base = offer.getOriginalFirstBuyItem().getCount();
            offer.increaseSpecialPrice(Math.max(1, (int) Math.ceil(base * malus)));
        }
    }
}
