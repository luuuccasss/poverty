package fr.luuuccasss.poverty;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;

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

    public static long today(ServerLevel level) {
        return level.getServer().overworld().getOverworldClockTime() / 24000L;
    }

    /** Appelé chaque seconde pour chaque joueur. */
    public static void tick(ServerLevel level, ServerPlayer player) {
        if (player.isSpectator() || player.isCreative()) return;
        Indebted account = (Indebted) player;
        long today = today(level);
        if (account.poverty$getLastDay() < 0) {
            grantLoan(player, today);
            return;
        }
        long missed = Math.min(today - account.poverty$getLastDay(), MAX_CATCH_UP_DAYS);
        account.poverty$setLastDay(today);
        for (int i = 0; i < missed && account.poverty$getDebt() > 0; i++) newDay(level, player);
    }

    private static void grantLoan(ServerPlayer player, long today) {
        Indebted account = (Indebted) player;
        account.poverty$setDebt(START_DEBT);
        account.poverty$setLastDay(today);
        player.sendSystemMessage(Component.literal("Bienvenue ! La banque du village t'a accordé un prêt de ")
            .append(emeralds(START_DEBT))
            .append(Component.literal(" à " + percent(DAILY_RATE) + " par jour. Bon courage."))
            .withStyle(ChatFormatting.GOLD));
    }

    /** Un nouveau jour : les intérêts tombent et sont prélevés directement dans l'inventaire. */
    public static void newDay(ServerLevel level, ServerPlayer player) {
        Indebted account = (Indebted) player;
        long interest = interest(account.poverty$getDebt());
        account.poverty$setDebt(account.poverty$getDebt() + interest);
        long due = account.poverty$getArrears() + interest;
        long paid = Money.take(player, due);
        account.poverty$setArrears(due - paid);

        if (account.poverty$getArrears() == 0) {
            account.poverty$setUnpaidDays(0);
            player.sendSystemMessage(Component.literal("Prélèvement automatique : ").withStyle(ChatFormatting.GRAY)
                .append(emeralds(paid).withStyle(ChatFormatting.RED))
                .append(Component.literal(" d'intérêts. Reste dû : ").withStyle(ChatFormatting.GRAY))
                .append(emeralds(account.poverty$getDebt()).withStyle(ChatFormatting.GOLD)));
        } else {
            account.poverty$setUnpaidDays(account.poverty$getUnpaidDays() + 1);
            player.sendSystemMessage(Component.literal("Incident de paiement ! ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("Impayés : ").withStyle(ChatFormatting.RED))
                .append(emeralds(account.poverty$getArrears()).withStyle(ChatFormatting.RED))
                .append(Component.literal(" (" + account.poverty$getUnpaidDays() + " jour"
                    + (account.poverty$getUnpaidDays() > 1 ? "s" : "") + " de retard)").withStyle(ChatFormatting.RED)));
            if (account.poverty$getUnpaidDays() == BAILIFF_AFTER_DAYS - 1) {
                player.sendSystemMessage(Component.literal("Dernier avertissement avant l'envoi d'un huissier.").withStyle(ChatFormatting.DARK_RED));
            }
        }
        collectors(level, player);
    }

    public static long interest(long debt) {
        return debt <= 0 ? 0 : (long) Math.ceil(debt * DAILY_RATE);
    }

    /** Les villageois du coin accourent réclamer leur dû. */
    private static void collectors(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.getRandom();
        for (Villager villager : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(32),
            v -> v.isAlive() && !v.isBaby() && !v.isSleeping())) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(player, 0.8f, 2));
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
            if (random.nextInt(2) == 0) {
                level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1f,
                    0.8f + random.nextFloat() * 0.3f);
            }
        }
    }

    /** Remboursement volontaire : d'abord les impayés, puis le capital. */
    public static void repay(ServerLevel level, ServerPlayer player, long amount) {
        Indebted account = (Indebted) player;
        account.poverty$setDebt(account.poverty$getDebt() - amount);
        account.poverty$setArrears(account.poverty$getArrears() - amount);
        if (account.poverty$getDebt() == 0) {
            freedom(level, player);
            return;
        }
        player.sendSystemMessage(Component.literal("Remboursé : ").withStyle(ChatFormatting.GRAY)
            .append(emeralds(amount).withStyle(ChatFormatting.GREEN))
            .append(Component.literal(". Reste dû : ").withStyle(ChatFormatting.GRAY))
            .append(emeralds(account.poverty$getDebt()).withStyle(ChatFormatting.GOLD)), true);
    }

    private static void freedom(ServerLevel level, ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Dette remboursée. Tu es enfin libre !").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(player.getName().getString()
            + " a remboursé toute sa dette !").withStyle(ChatFormatting.GREEN), false);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY(0.5), player.getZ(), 40, 1, 1, 1, 0.1);
        level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
    }

    /** Majoration des prix pour les mauvais payeurs : +25 % par jour de retard, jusqu'à +100 %. */
    public static double priceMalus(Indebted account) {
        return Math.min(1.0, account.poverty$getUnpaidDays() * 0.25);
    }

    public static MutableComponent emeralds(long amount) {
        return Component.literal(String.format("%,d", amount).replace(',', ' ') + " émeraude" + (amount > 1 ? "s" : ""));
    }

    private static String percent(double rate) {
        return (rate * 100 == Math.rint(rate * 100) ? String.valueOf((int) (rate * 100)) : String.valueOf(rate * 100)) + " %";
    }
}
