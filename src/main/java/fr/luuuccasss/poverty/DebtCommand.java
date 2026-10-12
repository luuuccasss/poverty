package fr.luuuccasss.poverty;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * /dette                         ton relevé de compte
 * /dette payer <montant>         rembourser, près d'un villageois
 * /dette <joueur>                le relevé d'un autre joueur (op)
 * /dette set <joueur> <montant>  régler la dette (op)
 * /dette jour <joueur>           faire passer un jour d'intérêts (op)
 * /dette huissier <joueur>       envoyer l'huissier tout de suite (op)
 */
public final class DebtCommand {
    private DebtCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("dette")
            .executes(ctx -> show(ctx.getSource(), ctx.getSource().getPlayerOrThrow()))
            .then(literal("payer")
                .then(argument("montant", LongArgumentType.longArg(1))
                    .executes(ctx -> pay(ctx.getSource(), LongArgumentType.getLong(ctx, "montant")))))
            .then(argument("joueur", EntityArgumentType.player())
                .requires(source -> source.hasPermissionLevel(2))
                .executes(ctx -> show(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "joueur"))))
            .then(literal("set")
                .requires(source -> source.hasPermissionLevel(2))
                .then(argument("joueur", EntityArgumentType.player())
                    .then(argument("montant", LongArgumentType.longArg(0))
                        .executes(ctx -> {
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "joueur");
                            Indebted account = (Indebted) target;
                            if (account.poverty$getLastDay() < 0) account.poverty$setLastDay(Bank.today(target.getWorld()));
                            account.poverty$setDebt(LongArgumentType.getLong(ctx, "montant"));
                            return show(ctx.getSource(), target);
                        }))))
            .then(literal("jour")
                .requires(source -> source.hasPermissionLevel(2))
                .then(argument("joueur", EntityArgumentType.player())
                    .executes(ctx -> {
                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "joueur");
                        Bank.newDay(target.getWorld(), target);
                        return show(ctx.getSource(), target);
                    })))
            .then(literal("huissier")
                .requires(source -> source.hasPermissionLevel(2))
                .then(argument("joueur", EntityArgumentType.player())
                    .executes(ctx -> {
                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "joueur");
                        Indebted account = (Indebted) target;
                        if (account.poverty$getArrears() <= 0) {
                            long amount = Math.max(1, Bank.interest(account.poverty$getDebt()));
                            account.poverty$setArrears(amount);
                        }
                        account.poverty$setUnpaidDays(Math.max(account.poverty$getUnpaidDays(), Bank.BAILIFF_AFTER_DAYS));
                        Bailiff.find(target.getWorld(), target).ifPresent(b -> b.discard());
                        Bailiff.send(target.getWorld(), target);
                        return 1;
                    }))));
    }

    private static int show(ServerCommandSource source, ServerPlayerEntity player) {
        Indebted account = (Indebted) player;
        source.sendFeedback(() -> Text.literal("Relevé de compte de ").formatted(Formatting.GRAY)
            .append(player.getDisplayName())
            .append(Text.literal("\n  Dette : ").formatted(Formatting.GRAY))
            .append(Bank.emeralds(account.poverty$getDebt()).formatted(Formatting.GOLD))
            .append(Text.literal("\n  Intérêts demain : ").formatted(Formatting.GRAY))
            .append(Bank.emeralds(Bank.interest(account.poverty$getDebt())).formatted(Formatting.YELLOW))
            .append(Text.literal("\n  Impayés : ").formatted(Formatting.GRAY))
            .append(Bank.emeralds(account.poverty$getArrears())
                .formatted(account.poverty$getArrears() > 0 ? Formatting.RED : Formatting.GREEN))
            .append(Text.literal(account.poverty$getUnpaidDays() > 0
                ? " (" + account.poverty$getUnpaidDays() + " j de retard)" : "").formatted(Formatting.RED))
            .append(Text.literal("\n  Dans ton inventaire : ").formatted(Formatting.GRAY))
            .append(Bank.emeralds(Money.count(player)).formatted(Formatting.GREEN)), false);
        return (int) Math.min(Integer.MAX_VALUE, account.poverty$getDebt());
    }

    private static int pay(ServerCommandSource source, long amount) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (player.getWorld().getEntitiesByClass(VillagerEntity.class, player.getBoundingBox().expand(8),
            v -> v.isAlive() && !v.isBaby()).isEmpty()) {
            source.sendError(Text.literal("La banque, c'est les villageois : approche-toi de l'un d'eux pour payer."));
            return 0;
        }
        long wanted = Math.min(amount, ((Indebted) player).poverty$getDebt());
        long paid = Money.take(player, wanted);
        if (paid == 0) {
            source.sendError(Text.literal("Tu n'as pas d'émeraudes sur toi."));
            return 0;
        }
        Bank.repay(player.getWorld(), player, paid);
        return (int) paid;
    }
}
