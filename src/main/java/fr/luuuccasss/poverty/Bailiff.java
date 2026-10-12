package fr.luuuccasss.poverty;

import fr.luuuccasss.poverty.mixin.MobAccessor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.levelgen.Heightmap;

/** L'huissier de justice : un zombie en costume qui vient saisir les biens des mauvais payeurs. */
public final class Bailiff {
    public static final String TAG = "poverty.huissier";
    private static final String CREDITOR_TAG = "poverty.pour.";
    /** Frais d'huissier, en part des impayés, ajoutés à son arrivée. */
    private static final double FEES = 0.10;

    /** Pris dans le registre : la constante a changé de classe entre 26.1 et 26.2. */
    @SuppressWarnings("unchecked")
    private static final EntityType<ZombieVillager> ZOMBIE_VILLAGER =
        (EntityType<ZombieVillager>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("zombie_villager"));

    /** Délai entre deux passages, quand l'huissier est reparti sans tout récupérer : une demi-journée. */
    private static final long REVISIT_DELAY = 12000;

    /** Joueur → huissier qui s'occupe de lui. */
    private static final Map<UUID, UUID> ASSIGNED = new HashMap<>();
    /** Joueur → heure du monde avant laquelle aucun huissier ne revient. */
    private static final Map<UUID, Long> NEXT_VISIT = new HashMap<>();

    private Bailiff() {
    }

    /** Appelé chaque seconde : envoie un huissier aux joueurs trop en retard. */
    public static void tick(ServerLevel level, ServerPlayer player) {
        Indebted account = (Indebted) player;
        if (account.poverty$getUnpaidDays() < Bank.BAILIFF_AFTER_DAYS || account.poverty$getArrears() <= 0) return;
        if (player.isSpectator() || player.isCreative() || !player.isAlive()) return;
        if (level.getGameTime() < NEXT_VISIT.getOrDefault(player.getUUID(), 0L)) return;
        if (find(level, player).isPresent()) return;
        send(level, player);
    }

    public static Optional<ZombieVillager> find(ServerLevel level, ServerPlayer player) {
        UUID id = ASSIGNED.get(player.getUUID());
        if (id == null) return Optional.empty();
        for (ServerLevel l : level.getServer().getAllLevels()) {
            Entity entity = l.getEntity(id);
            if (entity instanceof ZombieVillager bailiff && bailiff.isAlive()) return Optional.of(bailiff);
        }
        ASSIGNED.remove(player.getUUID());
        return Optional.empty();
    }

    public static ZombieVillager send(ServerLevel level, ServerPlayer player) {
        ZombieVillager bailiff = ZOMBIE_VILLAGER.create(level, EntitySpawnReason.EVENT);
        if (bailiff == null) return null;
        BlockPos pos = spawnPos(level, player);
        bailiff.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        bailiff.setVillagerData(bailiff.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.LIBRARIAN).withLevel(5));
        bailiff.setBaby(false);
        bailiff.setCustomName(Component.literal("Huissier de justice").withStyle(ChatFormatting.DARK_GRAY));
        bailiff.setCustomNameVisible(true);
        bailiff.setPersistenceRequired();
        bailiff.setCanPickUpLoot(false);
        bailiff.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);
        bailiff.setHealth(40);
        bailiff.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
        bailiff.setItemSlot(EquipmentSlot.HEAD, suit(Items.LEATHER_HELMET));
        bailiff.setItemSlot(EquipmentSlot.CHEST, suit(Items.LEATHER_CHESTPLATE));
        bailiff.setItemSlot(EquipmentSlot.LEGS, suit(Items.LEATHER_LEGGINGS));
        bailiff.setItemSlot(EquipmentSlot.FEET, suit(Items.LEATHER_BOOTS));
        bailiff.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WRITABLE_BOOK));
        for (EquipmentSlot slot : EquipmentSlot.values()) bailiff.setDropChance(slot, 0f);
        bailiff.addTag(TAG);
        bailiff.addTag(CREDITOR_TAG + player.getStringUUID());
        setUp(bailiff);
        level.addFreshEntity(bailiff);
        ASSIGNED.put(player.getUUID(), bailiff.getUUID());

        Indebted account = (Indebted) player;
        long fees = Math.max(1, Math.round(account.poverty$getArrears() * FEES));
        account.poverty$setDebt(account.poverty$getDebt() + fees);
        account.poverty$setArrears(account.poverty$getArrears() + fees);
        player.sendSystemMessage(Component.literal("Un huissier de justice arrive pour saisir tes biens. ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
            .append(Component.literal("Montant à recouvrer : ").withStyle(ChatFormatting.RED))
            .append(Bank.emeralds(account.poverty$getArrears()).withStyle(ChatFormatting.RED))
            .append(Component.literal(", frais d'huissier compris.").withStyle(ChatFormatting.RED)));
        level.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1f, 0.6f);
        return bailiff;
    }

    /** Remplace le cerveau de zombie : il ne mord personne, il saisit. */
    public static void setUp(ZombieVillager bailiff) {
        MobAccessor mob = (MobAccessor) bailiff;
        mob.poverty$goals().removeAllGoals(goal -> true);
        mob.poverty$targets().removeAllGoals(goal -> true);
        mob.poverty$goals().addGoal(0, new FloatGoal(bailiff));
        mob.poverty$goals().addGoal(1, new SeizeGoal(bailiff));
        mob.poverty$goals().addGoal(2, new LookAtPlayerGoal(bailiff, Player.class, 10f));
    }

    /** Remet en place un huissier rechargé depuis la sauvegarde. */
    public static void onLoad(Entity entity) {
        if (!(entity instanceof ZombieVillager bailiff) || !entity.entityTags().contains(TAG)) return;
        setUp(bailiff);
        creditor(bailiff).ifPresent(id -> ASSIGNED.put(id, bailiff.getUUID()));
    }

    public static Optional<UUID> creditor(Entity bailiff) {
        for (String tag : bailiff.entityTags()) {
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

    private static ItemStack suit(Item piece) {
        ItemStack stack = new ItemStack(piece);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0x1E1E24));
        return stack;
    }

    /** Un endroit à 12–20 blocs du joueur, à sa hauteur s'il est sous terre, sinon en surface. */
    private static BlockPos spawnPos(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            int distance = 12 + level.getRandom().nextInt(9);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            for (int dy = -4; dy <= 4; dy++) {
                BlockPos pos = new BlockPos(x, player.getBlockY() + dy, z);
                if (standable(level, pos)) return pos;
            }
        }
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(8, 0, 8));
        return standable(level, top) ? top : player.blockPosition();
    }

    private static boolean standable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos.below()).isRedstoneConductor(level, pos.below())
            && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
            && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
            && level.getFluidState(pos).isEmpty();
    }

    /** L'huissier repart : le prochain ne viendra pas avant une demi-journée. */
    public static void leftFrom(UUID debtor, long now) {
        NEXT_VISIT.put(debtor, now + REVISIT_DELAY);
    }

    public static void forget(ServerPlayer player) {
        ASSIGNED.remove(player.getUUID());
    }
}
