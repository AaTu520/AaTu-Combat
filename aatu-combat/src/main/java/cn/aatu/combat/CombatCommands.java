package cn.aatu.combat;

import cn.aatu.combat.model.PoolState;
import com.mojang.brigadier.Command;
import java.util.Collection;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class CombatCommands {
    private CombatCommands() {}

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aatucombat")
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> status(context.getSource(), EntityArgument.getEntity(context, "target")))))
                .then(Commands.literal("refill").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(context -> setPool(context.getSource(), EntityArgument.getEntities(context, "targets"), true))))
                .then(Commands.literal("empty").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(context -> setPool(context.getSource(), EntityArgument.getEntities(context, "targets"), false)))));
    }

    private static int status(CommandSourceStack source, Entity target) {
        if (!(target instanceof LivingEntity entity) || !CombatEvents.enabled(entity, CombatConfig.current())) {
            source.sendFailure(Component.literal("此目标未启用防护池。"));
            return 0;
        }
        PoolStore.Snapshot pool = PoolStore.snapshot(entity, CombatConfig.current());
        PoolStore.save(entity, pool.state(), pool.capacity());
        String message = String.format(Locale.ROOT, "%s：护甲 %.2f，韧性 %.2f，防护池 %.2f / %.2f",
                entity.getName().getString(), pool.armor(), pool.toughness(), pool.state().points(), pool.capacity());
        source.sendSuccess(() -> Component.literal(message), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setPool(CommandSourceStack source, Collection<? extends Entity> targets, boolean fill) {
        int count = 0;
        for (Entity target : targets) {
            if (target instanceof LivingEntity entity && CombatEvents.enabled(entity, CombatConfig.current())) {
                PoolStore.Snapshot pool = PoolStore.snapshot(entity, CombatConfig.current());
                long now = PoolStore.now(entity);
                PoolStore.save(entity, new PoolState(fill ? pool.capacity() : 0, now, now), pool.capacity());
                count++;
            }
        }
        int updated = count;
        source.sendSuccess(() -> Component.literal("已" + (fill ? "补满" : "清空") + " " + updated + " 个目标的防护池。"), true);
        return count;
    }
}
