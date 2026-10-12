package fr.luuuccasss.poverty;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Ce que fait l'huissier : il cherche les coffres autour de son débiteur, les vide de ce qui a le plus de valeur
 * jusqu'à couvrir les impayés, et faute de coffre il se sert directement dans les poches du joueur.
 */
public class SeizeGoal extends Goal {
    private static final int SEARCH_RADIUS = 12;
    private static final int SEIZE_COOLDOWN = 30;
    /** Au-delà de ce temps sans rien pouvoir faire, il abandonne et repart. */
    private static final int GIVE_UP_TICKS = 20 * 60 * 5;

    private final ZombieVillagerEntity bailiff;
    private final List<ItemStack> seized = new ArrayList<>();
    private long seizedValue;
    private BlockPos chest;
    private BlockPos openChest;
    private int closeIn;
    private int cooldown;
    private int idle;
    private int leaving = -1;

    public SeizeGoal(ZombieVillagerEntity bailiff) {
        this.bailiff = bailiff;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        return true;
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ServerWorld world = (ServerWorld) bailiff.getWorld();
        closeChest(world);
        if (leaving >= 0) {
            leave(world);
            return;
        }
        ServerPlayerEntity debtor = debtor(world);
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
        if (chest == null || !hasSomething(world, chest)) chest = findChest(world, debtor);
        if (chest != null) {
            bailiff.getLookControl().lookAt(Vec3d.ofCenter(chest));
            if (bailiff.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(chest)) > 4.5) {
                if (bailiff.age % 10 == 0) bailiff.getNavigation().startMovingTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 1.0);
                return;
            }
            bailiff.getNavigation().stop();
            seizeFromChest(world, debtor, chest);
        } else {
            bailiff.getLookControl().lookAt(debtor);
            if (bailiff.squaredDistanceTo(debtor) > 4) {
                if (bailiff.age % 10 == 0) bailiff.getNavigation().startMovingTo(debtor, 1.1);
                return;
            }
            bailiff.getNavigation().stop();
            seizeFromPockets(world, debtor);
        }
    }

    private ServerPlayerEntity debtor(ServerWorld world) {
        UUID id = Bailiff.creditor(bailiff).orElse(null);
        if (id == null) return null;
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);
        if (player == null || player.getWorld() != world || player.isSpectator()) return null;
        return player;
    }

    private BlockPos findChest(ServerWorld world, ServerPlayerEntity debtor) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos center = debtor.getBlockPos();
        for (BlockPos pos : BlockPos.iterate(center.add(-SEARCH_RADIUS, -6, -SEARCH_RADIUS), center.add(SEARCH_RADIUS, 6, SEARCH_RADIUS))) {
            if (!hasSomething(world, pos)) continue;
            double distance = bailiff.squaredDistanceTo(Vec3d.ofCenter(pos));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.toImmutable();
            }
        }
        return best;
    }

    private static boolean hasSomething(ServerWorld world, BlockPos pos) {
        BlockEntity entity = world.getBlockEntity(pos);
        return entity instanceof Inventory inventory && !(entity instanceof net.minecraft.block.entity.AbstractFurnaceBlockEntity)
            && !inventory.isEmpty();
    }

    private void seizeFromChest(ServerWorld world, ServerPlayerEntity debtor, BlockPos pos) {
        Inventory inventory = (Inventory) world.getBlockEntity(pos);
        int slot = mostValuable(inventory);
        if (slot < 0) return;
        open(world, pos);
        ItemStack stack = inventory.removeStack(slot);
        inventory.markDirty();
        take(world, debtor, stack, "dans ton coffre");
    }

    private void seizeFromPockets(ServerWorld world, ServerPlayerEntity debtor) {
        int slot = mostValuable(debtor.getInventory());
        if (slot < 0) {
            startLeaving(debtor, "L'huissier constate que tu n'as plus rien. Il note « insolvable » dans son carnet.");
            return;
        }
        ItemStack stack = debtor.getInventory().removeStack(slot);
        world.playSound(null, debtor.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.HOSTILE, 1f, 0.7f);
        take(world, debtor, stack, "sur toi");
    }

    /** Case de l'objet le plus précieux, ou de n'importe quoi s'il n'y a rien de précieux. */
    private static int mostValuable(Inventory inventory) {
        int best = -1;
        long bestValue = -1;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;
            long value = Appraisal.value(stack) + (Appraisal.isValuable(stack) ? 1_000_000 : 0);
            if (value > bestValue) {
                bestValue = value;
                best = i;
            }
        }
        return best;
    }

    private void take(ServerWorld world, ServerPlayerEntity debtor, ItemStack stack, String where) {
        Indebted account = (Indebted) debtor;
        long value = Appraisal.value(stack);
        seized.add(stack.copy());
        seizedValue += value;
        long debt = account.poverty$getDebt();
        account.poverty$setDebt(debt - value);
        account.poverty$setArrears(account.poverty$getArrears() - value);
        cooldown = SEIZE_COOLDOWN;
        idle = 0;
        bailiff.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, bailiff.getX(), bailiff.getEyeY() + 0.4, bailiff.getZ(), 1, 0, 0, 0, 0);
        debtor.sendMessage(Text.literal("Saisie " + where + " : ").formatted(Formatting.RED)
            .append(Text.literal(stack.getCount() + " × ").formatted(Formatting.WHITE))
            .append(stack.getName().copy().formatted(Formatting.WHITE))
            .append(Text.literal(" (estimé ").formatted(Formatting.RED))
            .append(Bank.emeralds(value).formatted(Formatting.GOLD))
            .append(Text.literal(")").formatted(Formatting.RED)), false);
        if (account.poverty$getDebt() == 0) Bank.repay(world, debtor, 0);
    }

    private void open(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock) world.addSyncedBlockEvent(pos, state.getBlock(), 1, 1);
        world.playSound(null, pos, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.6f, 0.9f);
        openChest = pos;
        closeIn = 25;
    }

    private void closeChest(ServerWorld world) {
        if (openChest == null || --closeIn > 0) return;
        BlockState state = world.getBlockState(openChest);
        if (state.getBlock() instanceof ChestBlock) world.addSyncedBlockEvent(openChest, state.getBlock(), 1, 0);
        world.playSound(null, openChest, SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.6f, 0.9f);
        openChest = null;
    }

    private void startLeaving(ServerPlayerEntity debtor, String message) {
        if (debtor != null && message != null) debtor.sendMessage(Text.literal(message).formatted(Formatting.GRAY), false);
        chest = null;
        leaving = 0;
        Bailiff.creditor(bailiff).ifPresent(id -> Bailiff.leftFrom(id, bailiff.getWorld().getTime()));
        if (debtor != null) {
            Vec3d away = bailiff.getPos().subtract(debtor.getPos()).multiply(1, 0, 1);
            if (away.lengthSquared() < 1.0E-4) away = new Vec3d(1, 0, 0);
            Vec3d target = bailiff.getPos().add(away.normalize().multiply(32));
            bailiff.getNavigation().startMovingTo(target.x, target.y, target.z, 1.0);
        }
    }

    /** Il s'éloigne quelques secondes puis disparaît, avec ce qu'il a saisi. */
    private void leave(ServerWorld world) {
        if (++leaving < 100) return;
        world.spawnParticles(ParticleTypes.POOF, bailiff.getX(), bailiff.getBodyY(0.5), bailiff.getZ(), 15, 0.3, 0.5, 0.3, 0.02);
        seized.clear();
        bailiff.discard();
    }

    /**
     * Tué par son débiteur : les biens saisis tombent par terre, mais leur valeur revient sur la dette
     * avec une amende pour outrage, et un autre huissier est envoyé.
     */
    public void onKilledBy(ServerWorld world, ServerPlayerEntity killer) {
        UUID id = Bailiff.creditor(bailiff).orElse(null);
        for (ItemStack stack : seized) {
            world.spawnEntity(new ItemEntity(world, bailiff.getX(), bailiff.getY() + 0.5, bailiff.getZ(), stack));
        }
        seized.clear();
        if (killer == null || !killer.getUuid().equals(id)) return;
        Indebted account = (Indebted) killer;
        long penalty = seizedValue + Bank.OUTRAGE_FEE;
        account.poverty$setDebt(account.poverty$getDebt() + penalty);
        account.poverty$setArrears(account.poverty$getArrears() + penalty);
        account.poverty$setUnpaidDays(Math.max(account.poverty$getUnpaidDays(), Bank.BAILIFF_AFTER_DAYS));
        killer.sendMessage(Text.literal("Outrage à un officier de justice ! ").formatted(Formatting.DARK_RED, Formatting.BOLD)
            .append(Text.literal("Amende de ").formatted(Formatting.RED))
            .append(Bank.emeralds(Bank.OUTRAGE_FEE).formatted(Formatting.RED))
            .append(Text.literal(", et un autre huissier est en route.").formatted(Formatting.RED)), false);
    }
}
