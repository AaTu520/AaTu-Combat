package cn.aatu.combat;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(CombatMod.MOD_ID)
public final class CombatMod {
    public static final String MOD_ID = "aatu_combat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CombatMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CombatConfig.SPEC, "aatu-combat.toml");
        modBus.addListener(this::loadConfig);
        modBus.addListener(CombatUiSync::register);
        modBus.addListener(DamageRecapSync::register);
        AttributeLimits.register(NeoForge.EVENT_BUS);
        CombatEvents.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, DamageRecapTrace::incoming);
        NeoForge.EVENT_BUS.addListener(CombatCommands::register);
        NeoForge.EVENT_BUS.addListener(CombatUiSync::tick);
        NeoForge.EVENT_BUS.addListener(ServerTickRate::tick);
        NeoForge.EVENT_BUS.addListener(CombatUiSync::logout);
        NeoForge.EVENT_BUS.addListener(CombatUiSync::stopped);
        NeoForge.EVENT_BUS.addListener(DamageRecapRecorder::tick);
        NeoForge.EVENT_BUS.addListener(DamageRecapRecorder::cloned);
        NeoForge.EVENT_BUS.addListener(DamageRecapRecorder::logout);
        NeoForge.EVENT_BUS.addListener(DamageRecapRecorder::stopped);
    }

    private void loadConfig(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == CombatConfig.SPEC) CombatConfig.load();
    }
}
