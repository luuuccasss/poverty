package fr.luuuccasss.poverty;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ce que fait l'huissier : il cherche les coffres autour de son débiteur, les vide de ce qui a le plus de valeur
 * jusqu'à couvrir les impayés, et faute de coffre il se sert directement dans les poches du joueur.
 */
public class SeizeGoal extends Goal {
    private static final int SEARCH_RADIUS = 12;
    private static final int SEIZE_COOLDOWN = 30;
    /** Au-delà de ce temps sans rien pouvoir faire, il abandonne et repart. */
    private static final int GIVE_UP_TICKS = 20 * 60 * 5;

    private final ZombieVillager bailiff;
    private final List<ItemStack> seized = new ArrayList<>();
    private long seizedValue;
    private BlockPos chest;
    private BlockPos openChest;
    private int closeIn;
    private int cooldown;
    private int idle;
    private int leaving = -1;

    public SeizeGoal(ZombieVillager bailiff) {
        this.bailiff = bailiff;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ServerLevel level = (ServerLevel) bailiff.level();
        closeChest(level);
        if (leaving >= 0) {
            leave(level);
            return;
        }
        ServerPlayer debtor = debtor(level);
        if (debtor == null) {
            startLeaving(null, null);
            return;
        }
        Indebted account = (Indebted) debtor;
        if (account.poverty$getArrears() <= 0) {
            startLeaving(debtor, "L'huissier range son carnet : ta dette est à jour. Pour l'instant.");
            return;
        }
        if (++idle > GIVE_UP_TICKS) {
            startLeaving(debtor, "L'huissier repart, il reviendra.");
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (chest == null || !hasSomething(level, chest)) chest = findChest(level, debtor);
        if (chest != null) {
            bailiff.getLookControl().setLookAt(Vec3.atCenterOf(chest));
            if (bailiff.position().distanceToSqr(Vec3.atBottomCenterOf(chest)) > 4.5) {
                if (bailiff.tickCount % 10 == 0) bailiff.getNavigation().moveTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 1.0);
                return;
            }
            bailiff.getNavigation().stop();
            seizeFromChest(level, debtor, chest);
        } else {
            bailiff.getLookControl().setLookAt(debtor);
            if (bailiff.distanceToSqr(debtor) > 4) {
                if (bailiff.tickCount % 10 == 0) bailiff.getNavigation().moveTo(debtor, 1.1);
                return;
            }
            bailiff.getNavigation().stop();
            seizeFromPockets(level, debtor);
        }
    }

    private ServerPlayer debtor(ServerLevel level) {
        UUID id = Bailiff.creditor(bailiff).orElse(null);
        if (id == null) return null;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
        if (player == null || player.level() != level || player.isSpectator()) return null;
        return player;
    }

    private BlockPos findChest(ServerLevel level, ServerPlayer debtor) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos center = debtor.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-SEARCH_RADIUS, -6, -SEARCH_RADIUS), center.offset(SEARCH_RADIUS, 6, SEARCH_RADIUS))) {
            if (!hasSomething(level, pos)) continue;
            double distance = bailiff.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    private static boolean hasSomething(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof Container container && !(entity instanceof AbstractFurnaceBlockEntity) && !container.isEmpty();
    }

    private void seizeFromChest(ServerLevel level, ServerPlayer debtor, BlockPos pos) {
        Container container = (Container) level.getBlockEntity(pos);
        int slot = mostValuable(container);
        if (slot < 0) return;
        open(level, pos);
        ItemStack stack = container.removeItemNoUpdate(slot);
        container.setChanged();
        take(level, debtor, stack, "dans ton coffre");
    }

    private void seizeFromPockets(ServerLevel level, ServerPlayer debtor) {
        int slot = mostValuable(debtor.getInventory());
        if (slot < 0) {
            startLeaving(debtor, "L'huissier constate que tu n'as plus rien. Il note « insolvable » dans son carnet.");
            return;
        }
        ItemStack stack = debtor.getInventory().removeItemNoUpdate(slot);
        level.playSound(null, debtor.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 1f, 0.7f);
        take(level, debtor, stack, "sur toi");
    }

    /** Case de l'objet le plus précieux, ou de n'importe quoi s'il n'y a rien de précieux. */
    private static int mostValuable(Container container) {
        int best = -1;
        long bestValue = -1;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            long value = Appraisal.value(stack) + (Appraisal.isValuable(stack) ? 1_000_000 : 0);
            if (value > bestValue) {
                bestValue = value;
                best = i;
            }
        }
        return best;
    }

    private void take(ServerLevel level, ServerPlayer debtor, ItemStack stack, String where) {
        Indebted account = (Indebted) debtor;
        long value = Appraisal.value(stack);
        seized.add(stack.copy());
        seizedValue += value;
        account.poverty$setDebt(account.poverty$getDebt() - value);
        account.poverty$setArrears(account.poverty$getArrears() - value);
        cooldown = SEIZE_COOLDOWN;
        idle = 0;
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, bailiff.getX(), bailiff.getEyeY() + 0.4, bailiff.getZ(), 1, 0, 0, 0, 0);
        debtor.sendSystemMessage(Component.literal("Saisie " + where + " : ").withStyle(ChatFormatting.RED)
            .append(Component.literal(stack.getCount() + " × ").withStyle(ChatFormatting.WHITE))
            .append(stack.getHoverName().copy().withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" (estimé ").withStyle(ChatFormatting.RED))
            .append(Bank.emeralds(value).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(")").withStyle(ChatFormatting.RED)));
        if (account.poverty$getDebt() == 0) Bank.repay(level, debtor, 0);
    }

    private void open(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock) level.blockEvent(pos, state.getBlock(), 1, 1);
        level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.6f, 0.9f);
        openChest = pos;
        closeIn = 25;
    }

    private void closeChest(ServerLevel level) {
        if (openChest == null || --closeIn > 0) return;
        BlockState state = level.getBlockState(openChest);
        if (state.getBlock() instanceof ChestBlock) level.blockEvent(openChest, state.getBlock(), 1, 0);
        level.playSound(null, openChest, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.6f, 0.9f);
        openChest = null;
    }

    private void startLeaving(ServerPlayer debtor, String message) {
        if (debtor != null && message != null) debtor.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GRAY));
        chest = null;
        leaving = 0;
        Bailiff.creditor(bailiff).ifPresent(id -> Bailiff.leftFrom(id, bailiff.level().getGameTime()));
        if (debtor != null) {
            Vec3 away = bailiff.position().subtract(debtor.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
            Vec3 target = bailiff.position().add(away.normalize().scale(32));
            bailiff.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
        }
    }

    /** Il s'éloigne quelques secondes puis disparaît, avec ce qu'il a saisi. */
    private void leave(ServerLevel level) {
        if (++leaving < 100) return;
        level.sendParticles(ParticleTypes.POOF, bailiff.getX(), bailiff.getY(0.5), bailiff.getZ(), 15, 0.3, 0.5, 0.3, 0.02);
        seized.clear();
        bailiff.discard();
    }

    /**
     * Tué par son débiteur : les biens saisis tombent par terre, mais leur valeur revient sur la dette
     * avec une amende pour outrage, et un autre huissier est envoyé.
     */
    public void onKilledBy(ServerLevel level, ServerPlayer killer) {
        UUID id = Bailiff.creditor(bailiff).orElse(null);
        for (ItemStack stack : seized) {
            level.addFreshEntity(new ItemEntity(level, bailiff.getX(), bailiff.getY() + 0.5, bailiff.getZ(), stack));
        }
        seized.clear();
        if (killer == null || !killer.getUUID().equals(id)) return;
        Indebted account = (Indebted) killer;
        long penalty = seizedValue + Bank.OUTRAGE_FEE;
        account.poverty$setDebt(account.poverty$getDebt() + penalty);
        account.poverty$setArrears(account.poverty$getArrears() + penalty);
        account.poverty$setUnpaidDays(Math.max(account.poverty$getUnpaidDays(), Bank.BAILIFF_AFTER_DAYS));
        killer.sendSystemMessage(Component.literal("Outrage à un officier de justice ! ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
            .append(Component.literal("Amende de ").withStyle(ChatFormatting.RED))
            .append(Bank.emeralds(Bank.OUTRAGE_FEE).withStyle(ChatFormatting.RED))
            .append(Component.literal(", et un autre huissier est en route.").withStyle(ChatFormatting.RED)));
    }
}
