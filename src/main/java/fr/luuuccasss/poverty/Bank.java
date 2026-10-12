package fr.luuuccasss.poverty;

import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;

/** La banque du village : prêt de départ, intérêts quotidiens, prélèvements et remboursements. */
public final class Bank {
    /** Prêt accordé d'office à chaque nouveau joueur. */
    public static final long START_DEBT = 10_000;
    /** Intérêts par jour de jeu (20 minutes). */
    public static final double DAILY_RATE = 0.01;
    /** Jours de retard avant l'arrivée de l'huissier. */
    public static final int BAILIFF_AFTER_DAYS = 2;
    /** Jours de retard à partir desquels plus personne ne commerce avec toi. */
    public static final int BLACKLIST_AFTER_DAYS = 4;
    /** Frais ajoutés à la dette quand on tue un huissier. */
    public static final long OUTRAGE_FEE = 1_000;
    /** Au retour d'une longue absence, on ne compte pas plus de jours que ça. */
    private static final int MAX_CATCH_UP_DAYS = 7;

    private Bank() {
    }

    public static long today(ServerWorld world) {
        return world.getServer().getOverworld().getTimeOfDay() / 24000L;
    }

    /** Appelé chaque seconde pour chaque joueur. */
    public static void tick(ServerWorld world, ServerPlayerEntity player) {
        if (player.isSpectator() || player.isCreative()) return;
        Indebted account = (Indebted) player;
        long today = today(world);
        if (account.poverty$getLastDay() < 0) {
            grantLoan(player, today);
            return;
        }
        long missed = Math.min(today - account.poverty$getLastDay(), MAX_CATCH_UP_DAYS);
        account.poverty$setLastDay(today);
        for (int i = 0; i < missed && account.poverty$getDebt() > 0; i++) newDay(world, player);
    }

    private static void grantLoan(ServerPlayerEntity player, long today) {
        Indebted account = (Indebted) player;
        account.poverty$setDebt(START_DEBT);
        account.poverty$setLastDay(today);
        player.sendMessage(Text.literal("Bienvenue ! La banque du village t'a accordé un prêt de ")
            .append(emeralds(START_DEBT))
            .append(Text.literal(" à " + percent(DAILY_RATE) + " par jour. Bon courage."))
            .formatted(Formatting.GOLD), false);
    }

    /** Un nouveau jour : les intérêts tombent et sont prélevés directement dans l'inventaire. */
    public static void newDay(ServerWorld world, ServerPlayerEntity player) {
        Indebted account = (Indebted) player;
        long interest = interest(account.poverty$getDebt());
        account.poverty$setDebt(account.poverty$getDebt() + interest);
        long due = account.poverty$getArrears() + interest;
        long paid = Money.take(player, due);
        account.poverty$setArrears(due - paid);

        if (account.poverty$getArrears() == 0) {
            account.poverty$setUnpaidDays(0);
            player.sendMessage(Text.literal("Prélèvement automatique : ").formatted(Formatting.GRAY)
                .append(emeralds(paid).formatted(Formatting.RED))
                .append(Text.literal(" d'intérêts. Reste dû : ").formatted(Formatting.GRAY))
                .append(emeralds(account.poverty$getDebt()).formatted(Formatting.GOLD)), false);
        } else {
            account.poverty$setUnpaidDays(account.poverty$getUnpaidDays() + 1);
            player.sendMessage(Text.literal("Incident de paiement ! ").formatted(Formatting.DARK_RED, Formatting.BOLD)
                .append(Text.literal("Impayés : ").formatted(Formatting.RED))
                .append(emeralds(account.poverty$getArrears()).formatted(Formatting.RED))
                .append(Text.literal(" (" + account.poverty$getUnpaidDays() + " jour"
                    + (account.poverty$getUnpaidDays() > 1 ? "s" : "") + " de retard)").formatted(Formatting.RED)), false);
            if (account.poverty$getUnpaidDays() == BAILIFF_AFTER_DAYS - 1) {
                player.sendMessage(Text.literal("Dernier avertissement avant l'envoi d'un huissier.").formatted(Formatting.DARK_RED), false);
            }
        }
        collectors(world, player);
    }

    public static long interest(long debt) {
        return debt <= 0 ? 0 : (long) Math.ceil(debt * DAILY_RATE);
    }

    /** Les villageois du coin accourent réclamer leur dû. */
    private static void collectors(ServerWorld world, ServerPlayerEntity player) {
        Random random = world.random;
        for (VillagerEntity villager : world.getEntitiesByClass(VillagerEntity.class, player.getBoundingBox().expand(32),
            v -> v.isAlive() && !v.isBaby() && !v.isSleeping())) {
            villager.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(player, 0.8f, 2));
            villager.getBrain().remember(MemoryModuleType.LOOK_TARGET, new net.minecraft.entity.ai.brain.EntityLookTarget(player, true));
            if (random.nextInt(2) == 0) {
                world.playSound(null, villager.getBlockPos(), SoundEvents.ENTITY_VILLAGER_TRADE, SoundCategory.NEUTRAL, 1f,
                    0.8f + random.nextFloat() * 0.3f);
            }
        }
    }

    /** Remboursement volontaire : d'abord les impayés, puis le capital. */
    public static void repay(ServerWorld world, ServerPlayerEntity player, long amount) {
        Indebted account = (Indebted) player;
        account.poverty$setDebt(account.poverty$getDebt() - amount);
        account.poverty$setArrears(account.poverty$getArrears() - amount);
        if (account.poverty$getDebt() == 0) {
            freedom(world, player);
            return;
        }
        player.sendMessage(Text.literal("Remboursé : ").formatted(Formatting.GRAY)
            .append(emeralds(amount).formatted(Formatting.GREEN))
            .append(Text.literal(". Reste dû : ").formatted(Formatting.GRAY))
            .append(emeralds(account.poverty$getDebt()).formatted(Formatting.GOLD)), true);
    }

    private static void freedom(ServerWorld world, ServerPlayerEntity player) {
        player.sendMessage(Text.literal("Dette remboursée. Tu es enfin libre !").formatted(Formatting.GREEN, Formatting.BOLD), false);
        world.getServer().getPlayerManager().broadcast(Text.literal(player.getName().getString()
            + " a remboursé toute sa dette !").formatted(Formatting.GREEN), false);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getBodyY(0.5), player.getZ(), 40, 1, 1, 1, 0.1);
        world.playSound(null, player.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1f, 1f);
    }

    /** Majoration des prix pour les mauvais payeurs : +25 % par jour de retard, jusqu'à +100 %. */
    public static double priceMalus(Indebted account) {
        return Math.min(1.0, account.poverty$getUnpaidDays() * 0.25);
    }

    public static net.minecraft.text.MutableText emeralds(long amount) {
        return Text.literal(String.format("%,d", amount).replace(',', ' ') + " émeraude" + (amount > 1 ? "s" : ""));
    }

    private static String percent(double rate) {
        return (rate * 100 == Math.rint(rate * 100) ? String.valueOf((int) (rate * 100)) : String.valueOf(rate * 100)) + " %";
    }
}
