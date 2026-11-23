package cn.aatu.combat;

import java.lang.reflect.Method;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

/** Optional, read-only access to Incapacitated 2.x player state. */
final class IncapacitatedLink {
    private static boolean checked;
    private static Object platform;
    private static Method getPlayerData;
    private static Method isIncapacitated;

    private IncapacitatedLink() {}

    static boolean isDown(Player player) {
        try {
            if (!checked) {
                checked = true;
                if (!ModList.get().isLoaded("incapacitated")) return false;
                Class<?> services = Class.forName("com.cartoonishvillain.incapacitated.platform.Services");
                platform = services.getField("PLATFORM").get(null);
                getPlayerData = platform.getClass().getMethod("getPlayerData", Player.class);
                isIncapacitated = Class.forName("com.cartoonishvillain.incapacitated.IncapacitatedPlayerData")
                        .getMethod("isIncapacitated");
            }
            return getPlayerData != null && Boolean.TRUE.equals(isIncapacitated.invoke(getPlayerData.invoke(platform, player)));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            getPlayerData = null;
            CombatMod.LOGGER.warn("Incapacitated state is unavailable; damage recap will still record hits and deaths", exception);
            return false;
        }
    }
}
