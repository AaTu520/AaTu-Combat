package cn.aatu.combat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

final class AttributeLimits {
    private static volatile MinecraftServer activeServer;
    private static SavedRange armorOverride, toughnessOverride;

    private AttributeLimits() {}

    static void register(IEventBus bus) {
        bus.addListener(EventPriority.LOWEST, AttributeLimits::starting);
        bus.addListener(AttributeLimits::stopped);
    }

    static boolean isActive(MinecraftServer server) {
        return server != null && server == activeServer;
    }

    private static void starting(ServerAboutToStartEvent event) {
        // AttributeFix initializes during FMLLoadCompleteEvent, after common setup.
        // World startup also runs for an integrated server, without changing a remote-only client.
        CombatConfig.load();
        CombatConfig.Settings settings = CombatConfig.current();
        if (!settings.enabled()) return;

        RangedAttribute armor = (RangedAttribute) Attributes.ARMOR.value();
        RangedAttribute toughness = (RangedAttribute) Attributes.ARMOR_TOUGHNESS.value();
        boolean attributeFix = ModList.get().isLoaded("attributefix");
        boolean keepHostLimits = settings.limitMode() == CombatConfig.LimitMode.HOST
                || settings.limitMode() == CombatConfig.LimitMode.AUTO && attributeFix;
        if (!keepHostLimits) {
            armorOverride = SavedRange.apply(armor, settings.armorMaximum());
            toughnessOverride = SavedRange.apply(toughness, settings.toughnessMaximum());
        }
        activeServer = event.getServer();
        CombatMod.LOGGER.info("Aatu Combat active: {} server; limits={}, AttributeFix={}, armor={}, toughness={}; pool uses actual attributes",
                activeServer.getClass().getSimpleName(),
                keepHostLimits ? "host" : "custom", attributeFix, armor.getMaxValue(), toughness.getMaxValue());
    }

    private static void stopped(ServerStoppedEvent event) {
        if (activeServer != event.getServer()) return;
        activeServer = null;
        if (armorOverride != null) armorOverride.restore();
        if (toughnessOverride != null) toughnessOverride.restore();
        armorOverride = null;
        toughnessOverride = null;
    }

    private record SavedRange(RangedAttribute attribute, double previousMin, double previousMax,
                              double appliedMin, double appliedMax) {
        static SavedRange apply(RangedAttribute attribute, double maximum) {
            double minimum = Math.min(attribute.getMinValue(), maximum);
            SavedRange original = new SavedRange(attribute, attribute.getMinValue(), attribute.getMaxValue(),
                    minimum, maximum);
            attribute.minValue = minimum;
            attribute.maxValue = maximum;
            return original;
        }

        void restore() {
            // Integrated-server attributes share a JVM with the client. Restore only ranges we
            // still own, so leaving a world cannot overwrite another mod's later adjustment.
            if (attribute.getMinValue() == appliedMin && attribute.getMaxValue() == appliedMax) {
                attribute.minValue = previousMin;
                attribute.maxValue = previousMax;
            }
        }
    }
}
