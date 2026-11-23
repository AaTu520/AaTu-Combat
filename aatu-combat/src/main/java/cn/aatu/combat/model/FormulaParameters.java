package cn.aatu.combat.model;

public record FormulaParameters(
        double armorPoolMultiplier,
        double toughnessPoolMultiplier,
        double synergyArmorReference,
        double maxArmorReduction,
        double armorDivisor,
        double minimumArmorFraction,
        double baseToughness,
        double toughnessDivisor,
        double soloToughnessReduction,
        double soloToughnessReference) {

    public static final FormulaParameters DEFAULT = new FormulaParameters(
            1, 1.5, 3000, 0.9, 25, 0.2, 2, 4, 0.05, 2000);
}
