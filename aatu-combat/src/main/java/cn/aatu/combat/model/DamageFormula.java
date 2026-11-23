package cn.aatu.combat.model;

public final class DamageFormula {
    private DamageFormula() {}

    public static double capacity(double armor, double toughness, FormulaParameters parameters) {
        requireFinite(armor, toughness);
        armor = Math.max(0, armor);
        toughness = Math.max(0, toughness);
        double synergy = Math.min(1, armor / parameters.synergyArmorReference());
        return armor * parameters.armorPoolMultiplier()
                + toughness * parameters.toughnessPoolMultiplier() * synergy;
    }

    public static Hit calculate(double damage, double armor, double toughness,
                                double availablePool, FormulaParameters parameters) {
        requireFinite(damage, armor, toughness, availablePool);
        damage = Math.max(0, damage);
        armor = Math.max(0, armor);
        toughness = Math.max(0, toughness);
        double spent = Math.min(damage, Math.max(0, availablePool));
        double remaining = damage - spent;
        double toughnessFactor = parameters.baseToughness() + toughness / parameters.toughnessDivisor();
        double effectiveArmor = Math.max(armor - remaining / toughnessFactor,
                armor * parameters.minimumArmorFraction());
        double armorReduction = clamp(effectiveArmor / parameters.armorDivisor(), 0, parameters.maxArmorReduction());
        double toughnessFloor = parameters.soloToughnessReduction()
                * clamp(toughness / parameters.soloToughnessReference(), 0, 1);
        return new Hit(spent, remaining, armorReduction, toughnessFloor);
    }

    public static double remainingDamage(Hit hit, double armorReduction, double maximumReduction) {
        requireFinite(armorReduction, maximumReduction);
        double reduction = clamp(Math.max(armorReduction, hit.toughnessFloor()), 0, maximumReduction);
        return hit.unmitigatedDamage() * (1 - reduction);
    }

    public static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Vanilla's inverted clamp bounds are unsafe when mods raise armor far beyond 100. */
    public static double safeVanillaFallback(double damage, double armor, double toughness) {
        if (!Double.isFinite(damage) || damage <= 0) return 0;
        armor = Double.isFinite(armor) ? Math.max(0, armor) : 0;
        toughness = Double.isFinite(toughness) ? Math.max(0, toughness) : 0;
        double effective = Math.max(armor - damage / (2 + toughness / 4), armor * 0.2);
        return damage * (1 - clamp(effective / 25, 0, 0.8));
    }

    private static void requireFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Combat inputs must be finite");
            }
        }
    }

    public record Hit(double poolSpent, double unmitigatedDamage,
                      double armorReduction, double toughnessFloor) {}
}
