package cn.aatu.combat;

/** Marks known direct attacks whose original damage source omits the attacker. */
public final class AttackDamageContext {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private AttackDamageContext() {}

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
