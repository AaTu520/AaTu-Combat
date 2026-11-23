package cn.aatu.combat;

/** Distinguishes damage caused by an active status effect from direct magic damage. */
public final class EffectDamageContext {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private EffectDamageContext() {}

    public static void enter() {
        DEPTH.get()[0]++;
    }

    public static void exit() {
        DEPTH.get()[0]--;
    }

    public static boolean active() {
        return DEPTH.get()[0] > 0;
    }
}
