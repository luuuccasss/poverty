package fr.luuuccasss.poverty;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

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

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("dette")
            .executes(ctx -> show(ctx.getSource(), ctx.getSource().getPlayerOrException()))
            .then(literal("payer")
                .then(argument("montant", LongArgumentType.longArg(1))
                    .executes(ctx -> pay(ctx.getSource(), LongArgumentType.getLong(ctx, "montant")))))
            .then(argument("joueur", EntityArgument.player())
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> show(ctx.getSource(), EntityArgument.getPlayer(ctx, "joueur"))))
            .then(literal("set")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("joueur", EntityArgument.player())
                    .then(argument("montant", LongArgumentType.longArg(0))
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "joueur");
                            Indebted account = (Indebted) target;
                            if (account.poverty$getLastDay() < 0) account.poverty$setLastDay(Bank.today(target.level()));
                            account.poverty$setDebt(LongArgumentType.getLong(ctx, "montant"));
                            return show(ctx.getSource(), target);
                        }))))
            .then(literal("jour")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("joueur", EntityArgument.player())
                    .executes(ctx -> {
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "joueur");
                        Bank.newDay(target.level(), target);
                        return show(ctx.getSource(), target);
                    })))
            .then(literal("huissier")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("joueur", EntityArgument.player())
                    .executes(ctx -> {
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "joueur");
                        Indebted account = (Indebted) target;
                        if (account.poverty$getArrears() <= 0) {
                            long amount = Math.max(1, Bank.interest(account.poverty$getDebt()));
                            account.poverty$setArrears(amount);
                        }
                        account.poverty$setUnpaidDays(Math.max(account.poverty$getUnpaidDays(), Bank.BAILIFF_AFTER_DAYS));
                        Bailiff.find(target.level(), target).ifPresent(b -> b.discard());
                        Bailiff.send(target.level(), target);
                        return 1;
                    }))));
    }

    private static int show(CommandSourceStack source, ServerPlayer player) {
        Indebted account = (Indebted) player;
        source.sendSuccess(() -> Component.literal("Relevé de compte de ").withStyle(ChatFormatting.GRAY)
            .append(player.getDisplayName())
            .append(Component.literal("\n  Dette : ").withStyle(ChatFormatting.GRAY))
            .append(Bank.emeralds(account.poverty$getDebt()).withStyle(ChatFormatting.GOLD))
            .append(Component.literal("\n  Intérêts demain : ").withStyle(ChatFormatting.GRAY))
            .append(Bank.emeralds(Bank.interest(account.poverty$getDebt())).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("\n  Impayés : ").withStyle(ChatFormatting.GRAY))
            .append(Bank.emeralds(account.poverty$getArrears())
                .withStyle(account.poverty$getArrears() > 0 ? ChatFormatting.RED : ChatFormatting.GREEN))
            .append(Component.literal(account.poverty$getUnpaidDays() > 0
                ? " (" + account.poverty$getUnpaidDays() + " j de retard)" : "").withStyle(ChatFormatting.RED))
            .append(Component.literal("\n  Dans ton inventaire : ").withStyle(ChatFormatting.GRAY))
            .append(Bank.emeralds(Money.count(player)).withStyle(ChatFormatting.GREEN)), false);
        return (int) Math.min(Integer.MAX_VALUE, account.poverty$getDebt());
    }

    private static int pay(CommandSourceStack source, long amount) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(8),
            v -> v.isAlive() && !v.isBaby()).isEmpty()) {
            source.sendFailure(Component.literal("La banque, c'est les villageois : approche-toi de l'un d'eux pour payer."));
            return 0;
        }
        long wanted = Math.min(amount, ((Indebted) player).poverty$getDebt());
        long paid = Money.take(player, wanted);
        if (paid == 0) {
            source.sendFailure(Component.literal("Tu n'as pas d'émeraudes sur toi."));
            return 0;
        }
        Bank.repay(player.level(), player, paid);
        return (int) paid;
    }
}
