package fr.luuuccasss.poverty.mixin;

import fr.luuuccasss.poverty.Bank;
import fr.luuuccasss.poverty.Indebted;
import fr.luuuccasss.poverty.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerMixin {
    /**
     * Accroupi avec des émeraudes en main : on rembourse sa dette au villageois.
     * Fiché comme mauvais payeur : plus personne ne commerce avec toi.
     */
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void poverty$interact(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.isCreative()) return;
        Villager self = (Villager) (Object) this;
        if (self.isBaby()) return;
        Indebted account = (Indebted) player;
        ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && Money.value(held) > 0 && account.poverty$getDebt() > 0) {
            long amount = Math.min(Money.value(held), account.poverty$getDebt());
            long paid = Money.take(serverPlayer, amount);
            Bank.repay((ServerLevel) self.level(), serverPlayer, paid);
            self.level().playSound(null, self.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        if (account.poverty$getUnpaidDays() >= Bank.BLACKLIST_AFTER_DAYS) {
            self.setUnhappyCounter(40);
            self.level().playSound(null, self.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, 0.8f);
            serverPlayer.sendSystemMessage(Component.literal("« Tu es fiché. Rembourse d'abord tes " + account.poverty$getArrears()
                + " émeraudes d'impayés. »").withStyle(ChatFormatting.RED), true);
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }

    /** Les mauvais payeurs paient plus cher. */
    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void poverty$badPayerPrices(Player player, CallbackInfo ci) {
        if (!(player instanceof Indebted account)) return;
        double malus = Bank.priceMalus(account);
        if (malus <= 0) return;
        for (MerchantOffer offer : ((Villager) (Object) this).getOffers()) {
            int base = offer.getBaseCostA().getCount();
            offer.addToSpecialPriceDiff(Math.max(1, (int) Math.ceil(base * malus)));
        }
    }
}
