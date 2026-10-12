package fr.luuuccasss.poverty;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Émeraudes et blocs d'émeraude dans l'inventaire d'un joueur. */
public final class Money {
    private Money() {
    }

    public static long count(ServerPlayer player) {
        long total = 0;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) total += value(inventory.getItem(i));
        return total;
    }

    /** Valeur en émeraudes d'une pile, si c'est de l'argent. */
    public static long value(ItemStack stack) {
        if (stack.is(Items.EMERALD)) return stack.getCount();
        if (stack.is(Items.EMERALD_BLOCK)) return stack.getCount() * 9L;
        return 0;
    }

    /**
     * Retire jusqu'à {@code amount} émeraudes de l'inventaire, en cassant des blocs si besoin
     * (la monnaie est rendue). Renvoie ce qui a été réellement pris.
     */
    public static long take(ServerPlayer player, long amount) {
        Inventory inventory = player.getInventory();
        long taken = 0;
        for (int i = 0; i < inventory.getContainerSize() && taken < amount; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(Items.EMERALD)) continue;
            int n = (int) Math.min(stack.getCount(), amount - taken);
            stack.shrink(n);
            taken += n;
        }
        for (int i = 0; i < inventory.getContainerSize() && taken < amount; i++) {
            ItemStack stack = inventory.getItem(i);
            while (stack.is(Items.EMERALD_BLOCK) && !stack.isEmpty() && taken < amount) {
                stack.shrink(1);
                long change = Math.max(0, 9 - (amount - taken));
                taken += 9 - change;
                if (change > 0) give(player, (int) change);
            }
        }
        inventory.setChanged();
        return taken;
    }

    private static void give(ServerPlayer player, int emeralds) {
        ItemStack change = new ItemStack(Items.EMERALD, emeralds);
        if (!player.getInventory().add(change)) {
            player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), change));
        }
    }
}
