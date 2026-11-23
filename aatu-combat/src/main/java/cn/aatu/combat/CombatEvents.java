package cn.aatu.combat;

import cn.aatu.combat.mixin.LivingEntityDamageAccess;
import cn.aatu.combat.model.DamageFormula;
import cn.aatu.combat.model.PoolState;
import java.util.Map;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class CombatEvents {
    private static final Map<DamageContainer, DamageFormula.Hit> PREVIEWS = new WeakHashMap<>();
    private static final Map<DamageContainer, Double> SPENDING = new WeakHashMap<>();
    private static final Set<LivingEntity> CHANGED_EQUIPMENT = Collections.newSetFromMap(new IdentityHashMap<>());
    private static long lastErrorNanos;

    private CombatEvents() {}

    static void register(IEventBus bus) {
        bus.addListener(EventPriority.LOWEST, CombatEvents::incoming);
        bus.addListener(EventPriority.LOWEST, CombatEvents::commit);
        bus.addListener(CombatEvents::equipment);
        bus.addListener(CombatEvents::login);
        bus.addListener(CombatEvents::clonePlayer);
        bus.addListener(CombatEvents::afterTick);
        bus.addListener(CombatEvents::stopped);
    }

    public static boolean enabled(LivingEntity entity, CombatConfig.Settings settings) {
        return settings != null && settings.enabled() && !entity.level().isClientSide()
                && AttributeLimits.isActive(entity.getServer());
    }

    /** Returns null when disabled; failed active calculations use bounded vanilla arithmetic. */
    public static Float preview(LivingEntity entity, float damage, DamageSource source, float armor, float toughness) {
        CombatConfig.Settings settings = CombatConfig.current();
        if (!enabled(entity, settings) || source.is(DamageTypeTags.BYPASSES_ARMOR)) return null;
        try {
            DamageContainer container = currentDamage(entity);
            boolean matchesCurrentDamage = container != null && Float.compare(damage, container.getNewDamage()) == 0;
            if (matchesCurrentDamage) PREVIEWS.remove(container);
            PoolStore.Snapshot pool = PoolStore.snapshot(entity, settings);
            DamageFormula.Hit hit = DamageFormula.calculate(damage, armor, toughness,
                    pool.state().points(), settings.formula());
            double reduction = hit.armorReduction();
            ItemStack weapon = source.getWeaponItem();
            if (weapon != null && entity.level() instanceof ServerLevel level) {
                reduction = EnchantmentHelper.modifyArmorEffectiveness(level, weapon,
                        entity, source, (float) reduction);
            }
            double remaining = DamageFormula.remainingDamage(hit, reduction, settings.formula().maxArmorReduction());
            if (!Double.isFinite(remaining)) throw new IllegalArgumentException("Non-finite damage result");
            // Youer can recalculate a Bukkit modifier using the original damage after the actual
            // armor pass. Do not let that preview overwrite the cost of an invulnerability delta.
            if (matchesCurrentDamage) {
                PREVIEWS.put(container, hit);
                DamageRecapTrace.armorPreview(container, armor, toughness, hit.poolSpent(), remaining);
            }
            return (float) remaining;
        } catch (RuntimeException exception) {
            reportFailure(exception);
            return (float) DamageFormula.safeVanillaFallback(damage, armor, toughness);
        }
    }

    static DamageContainer currentDamage(LivingEntity entity) {
        var containers = ((LivingEntityDamageAccess) entity).aatu$getDamageContainers();
        return containers.empty() ? null : containers.peek();
    }

    private static void incoming(LivingIncomingDamageEvent event) {
        if (!enabled(event.getEntity(), CombatConfig.current())) return;
        event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> {
            DamageFormula.Hit hit = PREVIEWS.remove(container);
            if (container.getNewDamage() > 0) {
                // These callbacks run after cancellation, blocking and invulnerability checks.
                // Retain other mods' result and remember only our successful preview's pool cost.
                SPENDING.put(container, hit == null ? 0.0 : hit.poolSpent());
            }
            return reduction;
        });
    }

    private static void commit(LivingDamageEvent.Pre event) {
        Double spent = SPENDING.remove(event.getContainer());
        if (spent == null || !enabled(event.getEntity(), CombatConfig.current())) return;
        try {
            PoolStore.Snapshot pool = PoolStore.snapshot(event.getEntity(), CombatConfig.current());
            PoolState state = pool.state().hit(spent, PoolStore.now(event.getEntity()));
            PoolStore.save(event.getEntity(), state, pool.capacity());
        } catch (RuntimeException exception) {
            reportFailure(exception);
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                ToughnessPoolStore.Snapshot pool = ToughnessPoolStore.snapshot(player, CombatConfig.current());
                ToughnessPoolStore.save(player, pool.state().hit(0, PoolStore.now(player)), pool.capacity());
            } catch (RuntimeException exception) {
                reportFailure(exception);
            }
        }
    }

    public static void healed(LivingEntity entity, float actualHealthGained) {
        CombatConfig.Settings settings = CombatConfig.current();
        if (!enabled(entity, settings) || !settings.healingEnabled() || actualHealthGained <= 0) return;
        try {
            PoolStore.Snapshot pool = PoolStore.snapshot(entity, settings);
            PoolState state = pool.state().heal(actualHealthGained, entity.getMaxHealth(),
                    pool.capacity(), settings.healingMultiplier());
            PoolStore.save(entity, state, pool.capacity());
        } catch (RuntimeException exception) {
            reportFailure(exception);
        }
        if (entity instanceof ServerPlayer player) {
            try {
                ToughnessPoolStore.Snapshot pool = ToughnessPoolStore.snapshot(player, settings);
                PoolState state = pool.state().heal(actualHealthGained, player.getMaxHealth(),
                        pool.capacity(), settings.healingMultiplier());
                ToughnessPoolStore.save(player, state, pool.capacity());
            } catch (RuntimeException exception) {
                reportFailure(exception);
            }
        }
    }

    private static void equipment(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (!enabled(entity, CombatConfig.current())) return;
        if (entity.getPersistentData().contains(PoolStore.KEY)) refresh(entity);
        // NeoForge posts this event before replacing attribute modifiers; settle the new cap afterward.
        CHANGED_EQUIPMENT.add(entity);
    }

    private static void afterTick(ServerTickEvent.Post event) {
        for (LivingEntity entity : CHANGED_EQUIPMENT) {
            if (!entity.isRemoved()) refresh(entity);
        }
        CHANGED_EQUIPMENT.clear();
    }

    private static void stopped(ServerStoppedEvent event) {
        PREVIEWS.clear();
        SPENDING.clear();
        CHANGED_EQUIPMENT.clear();
        lastErrorNanos = 0;
    }

    private static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (enabled(event.getEntity(), CombatConfig.current())) refresh(event.getEntity());
    }

    private static void clonePlayer(PlayerEvent.Clone event) {
        var previous = event.getOriginal().getPersistentData();
        if (previous.contains(PoolStore.KEY)) {
            event.getEntity().getPersistentData().put(PoolStore.KEY, previous.getCompound(PoolStore.KEY).copy());
        }
        if (previous.contains(ToughnessPoolStore.KEY)) {
            event.getEntity().getPersistentData().put(ToughnessPoolStore.KEY,
                    previous.getCompound(ToughnessPoolStore.KEY).copy());
        }
    }

    private static void refresh(LivingEntity entity) {
        try { PoolStore.refresh(entity, CombatConfig.current()); }
        catch (RuntimeException exception) { reportFailure(exception); }
        if (entity instanceof ServerPlayer player) {
            try { ToughnessPoolStore.refresh(player, CombatConfig.current()); }
            catch (RuntimeException exception) { reportFailure(exception); }
        }
    }

    static void reportFailure(RuntimeException exception) {
        long now = System.nanoTime();
        if (lastErrorNanos == 0 || now - lastErrorNanos > 60_000_000_000L) {
            lastErrorNanos = now;
            CombatMod.LOGGER.error("Aatu Combat could not process this operation; repeated errors are limited to one per minute", exception);
        }
    }
}
