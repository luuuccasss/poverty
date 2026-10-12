package fr.luuuccasss.poverty;

import fr.luuuccasss.poverty.mixin.MobEntityAccessor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.Heightmap;

/** L'huissier de justice : un zombie en costume qui vient saisir les biens des mauvais payeurs. */
public final class Bailiff {
    public static final String TAG = "poverty.huissier";
    private static final String CREDITOR_TAG = "poverty.pour.";
    /** Frais d'huissier, en part des impayés, ajoutés à son arrivée. */
    private static final double FEES = 0.10;

    /** Délai entre deux passages, quand l'huissier est reparti sans tout récupérer : une demi-journée. */
    private static final long REVISIT_DELAY = 12000;

    /** Joueur → huissier qui s'occupe de lui. */
    private static final Map<UUID, UUID> ASSIGNED = new HashMap<>();
    /** Joueur → heure du monde avant laquelle aucun huissier ne revient. */
    private static final Map<UUID, Long> NEXT_VISIT = new HashMap<>();

    private Bailiff() {
    }

    /** Appelé chaque seconde : envoie un huissier aux joueurs trop en retard. */
    public static void tick(ServerWorld world, ServerPlayerEntity player) {
        Indebted account = (Indebted) player;
        if (account.poverty$getUnpaidDays() < Bank.BAILIFF_AFTER_DAYS || account.poverty$getArrears() <= 0) return;
        if (player.isSpectator() || player.isCreative() || !player.isAlive()) return;
        if (world.getTime() < NEXT_VISIT.getOrDefault(player.getUuid(), 0L)) return;
        if (find(world, player).isPresent()) return;
        send(world, player);
    }

    public static Optional<ZombieVillagerEntity> find(ServerWorld world, ServerPlayerEntity player) {
        UUID id = ASSIGNED.get(player.getUuid());
        if (id == null) return Optional.empty();
        for (ServerWorld w : world.getServer().getWorlds()) {
            Entity entity = w.getEntity(id);
            if (entity instanceof ZombieVillagerEntity bailiff && bailiff.isAlive()) return Optional.of(bailiff);
        }
        ASSIGNED.remove(player.getUuid());
        return Optional.empty();
    }

    public static ZombieVillagerEntity send(ServerWorld world, ServerPlayerEntity player) {
        ZombieVillagerEntity bailiff = EntityType.ZOMBIE_VILLAGER.create(world, SpawnReason.EVENT);
        if (bailiff == null) return null;
        BlockPos pos = spawnPos(world, player);
        bailiff.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360, 0);
        bailiff.setVillagerData(bailiff.getVillagerData().withProfession(world.getRegistryManager(), VillagerProfession.LIBRARIAN).withLevel(5));
        bailiff.setBaby(false);
        bailiff.setCustomName(Text.literal("Huissier de justice").formatted(Formatting.DARK_GRAY));
        bailiff.setCustomNameVisible(true);
        bailiff.setPersistent();
        bailiff.setCanPickUpLoot(false);
        bailiff.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(40);
        bailiff.setHealth(40);
        bailiff.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(0.3);
        bailiff.equipStack(EquipmentSlot.HEAD, suit(Items.LEATHER_HELMET));
        bailiff.equipStack(EquipmentSlot.CHEST, suit(Items.LEATHER_CHESTPLATE));
        bailiff.equipStack(EquipmentSlot.LEGS, suit(Items.LEATHER_LEGGINGS));
        bailiff.equipStack(EquipmentSlot.FEET, suit(Items.LEATHER_BOOTS));
        bailiff.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.WRITABLE_BOOK));
        for (EquipmentSlot slot : EquipmentSlot.values()) bailiff.setEquipmentDropChance(slot, 0f);
        bailiff.addCommandTag(TAG);
        bailiff.addCommandTag(CREDITOR_TAG + player.getUuidAsString());
        setUp(bailiff);
        world.spawnEntity(bailiff);
        ASSIGNED.put(player.getUuid(), bailiff.getUuid());

        Indebted account = (Indebted) player;
        long fees = Math.max(1, Math.round(account.poverty$getArrears() * FEES));
        account.poverty$setDebt(account.poverty$getDebt() + fees);
        account.poverty$setArrears(account.poverty$getArrears() + fees);
        player.sendMessage(Text.literal("Un huissier de justice arrive pour saisir tes biens. ").formatted(Formatting.DARK_RED, Formatting.BOLD)
            .append(Text.literal("Montant à recouvrer : ").formatted(Formatting.RED))
            .append(Bank.emeralds(account.poverty$getArrears()).formatted(Formatting.RED))
            .append(Text.literal(", frais d'huissier compris.").formatted(Formatting.RED)), false);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.HOSTILE, 1f, 0.6f);
        return bailiff;
    }

    /** Remplace le cerveau de zombie : il ne mord personne, il saisit. */
    public static void setUp(ZombieVillagerEntity bailiff) {
        MobEntityAccessor mob = (MobEntityAccessor) bailiff;
        mob.poverty$goals().clear(goal -> true);
        mob.poverty$targets().clear(goal -> true);
        mob.poverty$goals().add(0, new SwimGoal(bailiff));
        mob.poverty$goals().add(1, new SeizeGoal(bailiff));
        mob.poverty$goals().add(2, new LookAtEntityGoal(bailiff, PlayerEntity.class, 10f));
    }

    /** Remet en place un huissier rechargé depuis la sauvegarde. */
    public static void onLoad(Entity entity) {
        if (!(entity instanceof ZombieVillagerEntity bailiff) || !entity.getCommandTags().contains(TAG)) return;
        setUp(bailiff);
        creditor(bailiff).ifPresent(id -> ASSIGNED.put(id, bailiff.getUuid()));
    }

    public static Optional<UUID> creditor(Entity bailiff) {
        for (String tag : bailiff.getCommandTags()) {
            if (tag.startsWith(CREDITOR_TAG)) {
                try {
                    return Optional.of(UUID.fromString(tag.substring(CREDITOR_TAG.length())));
                } catch (IllegalArgumentException ignored) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private static ItemStack suit(net.minecraft.item.Item piece) {
        ItemStack stack = new ItemStack(piece);
        stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(0x1E1E24));
        return stack;
    }

    /** Un endroit à 12–20 blocs du joueur, à sa hauteur s'il est sous terre, sinon en surface. */
    private static BlockPos spawnPos(ServerWorld world, ServerPlayerEntity player) {
        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            int distance = 12 + world.random.nextInt(9);
            int x = MathHelper.floor(player.getX() + Math.cos(angle) * distance);
            int z = MathHelper.floor(player.getZ() + Math.sin(angle) * distance);
            for (int dy = -4; dy <= 4; dy++) {
                BlockPos pos = new BlockPos(x, player.getBlockY() + dy, z);
                if (standable(world, pos)) return pos;
            }
        }
        BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, player.getBlockPos().add(8, 0, 8));
        return standable(world, top) ? top : player.getBlockPos();
    }

    private static boolean standable(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos.down()).isSolidBlock(world, pos.down())
            && world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
            && world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty()
            && world.getFluidState(pos).isEmpty();
    }

    /** L'huissier repart : le prochain ne viendra pas avant une demi-journée. */
    public static void leftFrom(UUID debtor, long now) {
        NEXT_VISIT.put(debtor, now + REVISIT_DELAY);
    }

    public static void forget(ServerPlayerEntity player) {
        ASSIGNED.remove(player.getUuid());
    }
}
