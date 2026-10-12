package fr.luuuccasss.poverty;

import java.util.Map;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ToolItem;
import net.minecraft.item.ToolMaterials;

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
        if (stack.hasEnchantments()) unit += 5;
        double total = unit * stack.getCount();
        return Math.max(1, Math.round(total));
    }

    /** Vrai si l'objet a une valeur propre (pas du bric-à-brac). */
    public static boolean isValuable(ItemStack stack) {
        return !stack.isEmpty() && (unitPrice(stack) > 0 || stack.hasEnchantments());
    }

    private static double unitPrice(ItemStack stack) {
        Double price = PRICES.get(stack.getItem());
        if (price != null) return price;
        if (stack.getItem() instanceof ToolItem tool) return material(tool.getMaterial() == ToolMaterials.NETHERITE,
            tool.getMaterial() == ToolMaterials.DIAMOND, tool.getMaterial() == ToolMaterials.GOLD, tool.getMaterial() == ToolMaterials.IRON);
        if (stack.getItem() instanceof ArmorItem armor) return material(armor.getMaterial().equals(ArmorMaterials.NETHERITE),
            armor.getMaterial().equals(ArmorMaterials.DIAMOND), armor.getMaterial().equals(ArmorMaterials.GOLD), armor.getMaterial().equals(ArmorMaterials.IRON));
        return 0;
    }

    private static double material(boolean netherite, boolean diamond, boolean gold, boolean iron) {
        if (netherite) return 80;
        if (diamond) return 20;
        if (gold) return 4;
        if (iron) return 3;
        return 0;
    }
}
