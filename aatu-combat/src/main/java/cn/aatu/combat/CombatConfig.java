package cn.aatu.combat;

import cn.aatu.combat.model.FormulaParameters;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class CombatConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED, IDLE_ENABLED, HEAL_ENABLED, DAMAGE_RECAP_ENABLED;
    private static final ModConfigSpec.EnumValue<LimitMode> LIMIT_MODE;
    private static final ModConfigSpec.DoubleValue ARMOR_MAX, TOUGHNESS_MAX, ARMOR_POOL, TOUGHNESS_POOL,
            SYNERGY_REFERENCE, REDUCTION_MAX, ARMOR_DIVISOR, MIN_ARMOR, BASE_TOUGHNESS,
            TOUGHNESS_DIVISOR, SOLO_REDUCTION, SOLO_REFERENCE, IDLE_DELAY, IDLE_RATE, HEAL_MULTIPLIER,
            BYPASS_TOUGHNESS_POOL;
    private static volatile Settings current;
    private static volatile boolean damageRecapEnabled = true;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("服务端配置。修改后重启服务器生效；两个恢复方式可以同时开启。")
                .push("general");
        ENABLED = builder.comment("总开关。关闭后恢复宿主原有伤害公式和属性上限。")
                .define("enabled", true);
        builder.pop().push("limits");
        LIMIT_MODE = builder.comment("AUTO：安装 AttributeFix 时沿用其上限，否则使用下面的自定义上限。",
                        "CUSTOM：始终使用下面的护甲/韧性上限。HOST：始终保留宿主或其他模组设置。",
                        "防护池始终根据实际生效属性计算，不固定为 6000，也不会按属性上限直接填满。")
                .defineEnum("mode", LimitMode.AUTO);
        ARMOR_MAX = number(builder, "armorMaximum", 3000, 0, 1_000_000, "使用自定义上限时的护甲上限。");
        TOUGHNESS_MAX = number(builder, "toughnessMaximum", 2000, 0, 1_000_000, "使用自定义上限时的护甲韧性上限。");
        builder.pop().comment("防护池 = 护甲×armorMultiplier + 韧性×toughnessMultiplier×min(护甲/synergyArmorReference, 1)。")
                .push("pool");
        ARMOR_POOL = number(builder, "armorMultiplier", 1, 0, 1000, "护甲提供的防护池倍率。");
        TOUGHNESS_POOL = number(builder, "toughnessMultiplier", 1.5, 0, 1000, "韧性提供的协同倍率；没有护甲时不提供防护池。");
        SYNERGY_REFERENCE = number(builder, "synergyArmorReference", 3000, 0.001, 1_000_000, "韧性完全发挥作用所需护甲。");
        builder.pop().comment("先扣防护池，再对剩余伤害计算减伤；附魔与抗性随后由原流程处理。")
                .push("damage");
        REDUCTION_MAX = number(builder, "maximumReduction", 0.9, 0, 1, "护甲/韧性的减伤上限；0.9 = 90%。不限制后续抗性等其他减伤。");
        ARMOR_DIVISOR = number(builder, "armorDivisor", 25, 0.001, 1_000_000, "有效护甲转为减伤率时的除数。");
        MIN_ARMOR = number(builder, "minimumArmorFraction", 0.2, 0, 1, "高伤害时保留的护甲比例。");
        BASE_TOUGHNESS = number(builder, "baseToughness", 2, 0.001, 1_000_000, "削甲公式分母的基础值。");
        TOUGHNESS_DIVISOR = number(builder, "toughnessDivisor", 4, 0.001, 1_000_000, "分母 = baseToughness + 韧性/toughnessDivisor。");
        SOLO_REDUCTION = number(builder, "soloToughnessReduction", 0.05, 0, 1, "韧性独立减伤最多 5%；仍受 maximumReduction 限制。");
        SOLO_REFERENCE = number(builder, "soloToughnessReference", 2000, 0.001, 1_000_000, "达到独立韧性减伤上限所需的韧性。");
        builder.pop().push("bypassProtection");
        BYPASS_TOUGHNESS_POOL = number(builder, "toughnessPoolMultiplier", 1.25, 0, 1000,
                "直接破甲抵伤使用的独立韧性池容量倍率；每 5 点池抵挡 1 点伤害，单次最多抵挡 50%。");
        builder.pop().push("damageRecap");
        DAMAGE_RECAP_ENABLED = builder.comment("向支持回放的本人客户端同步最近受伤和倒地/死亡记录；独立于伤害公式开关。")
                .define("enabled", true);
        builder.pop().push("recovery").push("idle");
        IDLE_ENABLED = builder.comment("启用未受击恢复。被防护池完全挡住的有效攻击也重置等待时间。")
                .define("enabled", true);
        IDLE_DELAY = number(builder, "delaySeconds", 3, 0, 86400, "停止受击后等待的秒数（20 游戏刻 = 1 秒）。");
        IDLE_RATE = number(builder, "fractionPerSecond", 0.05, 0, 1, "每秒恢复最大防护池的比例；0.05 = 5%。");
        builder.pop().push("healing");
        HEAL_ENABLED = builder.comment("统一处理自然回血、瞬间治疗、生命恢复等经 heal 接口产生的实际回血。")
                .define("enabled", true);
        HEAL_MULTIPLIER = number(builder, "multiplier", 1, 0, 1000, "恢复池 = 最大池×实际回血/最大生命×此倍率。满血、被取消的治疗不恢复。");
        builder.pop().pop();
        SPEC = builder.build();
    }

    private CombatConfig() {}

    private static ModConfigSpec.DoubleValue number(ModConfigSpec.Builder builder, String name,
                                                    double value, double min, double max, String comment) {
        return builder.comment(comment).defineInRange(name, value, min, max);
    }

    public static void load() {
        damageRecapEnabled = DAMAGE_RECAP_ENABLED.get();
        FormulaParameters formula = new FormulaParameters(ARMOR_POOL.get(), TOUGHNESS_POOL.get(),
                SYNERGY_REFERENCE.get(), REDUCTION_MAX.get(), ARMOR_DIVISOR.get(), MIN_ARMOR.get(),
                BASE_TOUGHNESS.get(), TOUGHNESS_DIVISOR.get(), SOLO_REDUCTION.get(), SOLO_REFERENCE.get());
        current = new Settings(ENABLED.get(), LIMIT_MODE.get(), ARMOR_MAX.get(), TOUGHNESS_MAX.get(),
                formula, IDLE_ENABLED.get(), Math.round(IDLE_DELAY.get() * 20), IDLE_RATE.get(),
                HEAL_ENABLED.get(), HEAL_MULTIPLIER.get(), BYPASS_TOUGHNESS_POOL.get());
    }

    public static Settings current() { return current; }
    public static boolean damageRecapEnabled() { return damageRecapEnabled; }

    public enum LimitMode { AUTO, CUSTOM, HOST }

    public record Settings(boolean enabled, LimitMode limitMode, double armorMaximum, double toughnessMaximum,
                           FormulaParameters formula, boolean idleEnabled, long idleDelayTicks,
                           double idleFractionPerSecond, boolean healingEnabled, double healingMultiplier,
                           double bypassToughnessPoolMultiplier) {}
}
