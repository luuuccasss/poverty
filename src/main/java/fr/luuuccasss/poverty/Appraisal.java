package fr.luuuccasss.poverty;

import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Ce que l'huissier estime que valent tes affaires, en émeraudes. */
public final class Appraisal {
    private static final Map<Item, Double> PRICES = Map.ofEntries(
        Map.entry(Items.EMERALD, 1.0), Map.entry(Items.EMERALD_BLOCK, 9.0),
        Map.entry(Items.DIAMOND, 8.0), Map.entry(Items.DIAMOND_BLOCK, 72.0),
        Map.entry(Items.NETHERITE_SCRAP, 16.0), Map.entry(Items.ANCIENT_DEBRIS, 16.0),
        Map.entry(Items.NETHERITE_INGOT, 64.0), Map.entry(Items.NETHERITE_BLOCK, 576.0),
        Map.entry(Items.GOLD_INGOT, 2.0), Map.entry(Items.GOLD_BLOCK, 18.0), Map.entry(Items.RAW_GOLD, 1.5),
        Map.entry(Items.IRON_INGOT, 1.0), Map.entry(Items.IRON_BLOCK, 9.0), Map.entry(Items.RAW_IRON, 0.75),
        Map.entry(Items.LAPIS_LAZULI, 0.25), Map.entry(Items.REDSTONE, 0.1), Map.entry(Items.COPPER_INGOT, 0.25),
        Map.entry(Items.GOLDEN_APPLE, 8.0), Map.entry(Items.ENCHANTED_GOLDEN_APPLE, 100.0),
        Map.entry(Items.TOTEM_OF_UNDYING, 30.0), Map.entry(Items.ELYTRA, 150.0), Map.entry(Items.BEACON, 200.0),
        Map.entry(Items.NETHER_STAR, 150.0), Map.entry(Items.ENCHANTED_BOOK, 6.0), Map.entry(Items.TRIDENT, 40.0),
        Map.entry(Items.SADDLE, 6.0), Map.entry(Items.NAME_TAG, 5.0), Map.entry(Items.EXPERIENCE_BOTTLE, 1.0),
        Map.entry(Items.SHULKER_SHELL, 10.0), Map.entry(Items.ENDER_PEARL, 1.0), Map.entry(Items.BLAZE_ROD, 1.0));

    private Appraisal() {
    }

    /** Valeur d'une pile entière. Les objets sans valeur particulière comptent pour une émeraude la pile. */
    public static long value(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        double unit = unitPrice(stack);
        if (stack.isEnchanted()) unit += 5;
        double total = unit * stack.getCount();
        return Math.max(1, Math.round(total));
    }

    /** Vrai si l'objet a une valeur propre (pas du bric-à-brac). */
    public static boolean isValuable(ItemStack stack) {
        return !stack.isEmpty() && (unitPrice(stack) > 0 || stack.isEnchanted());
    }

    private static double unitPrice(ItemStack stack) {
        Double price = PRICES.get(stack.getItem());
        if (price != null) return price;
        if (!stack.isDamageableItem()) return 0;
        // outils et armures : d'après leur matériau (netherite_sword, diamond_helmet…)
        String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (name.startsWith("netherite_")) return 80;
        if (name.startsWith("diamond_")) return 20;
        if (name.startsWith("golden_")) return 4;
        if (name.startsWith("iron_") || name.startsWith("chainmail_")) return 3;
        return 0;
    }
}
