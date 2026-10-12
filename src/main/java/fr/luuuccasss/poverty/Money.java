package fr.luuuccasss.poverty;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

/** Émeraudes et blocs d'émeraude dans l'inventaire d'un joueur. */
public final class Money {
    private Money() {
    }

    public static long count(ServerPlayerEntity player) {
        long total = 0;
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) total += value(inventory.getStack(i));
        return total;
    }

    /** Valeur en émeraudes d'une pile, si c'est de l'argent. */
    public static long value(ItemStack stack) {
        if (stack.isOf(Items.EMERALD)) return stack.getCount();
        if (stack.isOf(Items.EMERALD_BLOCK)) return stack.getCount() * 9L;
        return 0;
    }

    /**
     * Retire jusqu'à {@code amount} émeraudes de l'inventaire, en cassant des blocs si besoin
     * (la monnaie est rendue). Renvoie ce qui a été réellement pris.
     */
    public static long take(ServerPlayerEntity player, long amount) {
        PlayerInventory inventory = player.getInventory();
        long taken = 0;
        for (int i = 0; i < inventory.size() && taken < amount; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isOf(Items.EMERALD)) continue;
            int n = (int) Math.min(stack.getCount(), amount - taken);
            stack.decrement(n);
            taken += n;
        }
        for (int i = 0; i < inventory.size() && taken < amount; i++) {
            ItemStack stack = inventory.getStack(i);
            while (stack.isOf(Items.EMERALD_BLOCK) && !stack.isEmpty() && taken < amount) {
                stack.decrement(1);
                long change = Math.max(0, 9 - (amount - taken));
                taken += 9 - change;
                if (change > 0) give(player, (int) change);
            }
        }
        inventory.markDirty();
        return taken;
    }

    private static void give(ServerPlayerEntity player, int emeralds) {
        ItemStack change = new ItemStack(Items.EMERALD, emeralds);
        if (!player.getInventory().insertStack(change)) player.dropItem(change, false);
    }
}
