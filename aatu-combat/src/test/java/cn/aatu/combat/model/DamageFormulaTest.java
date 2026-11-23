package cn.aatu.combat.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DamageFormulaTest {
    private static final FormulaParameters RULES = FormulaParameters.DEFAULT;

    private double damage(double incoming, double armor, double toughness, double pool) {
        var hit = DamageFormula.calculate(incoming, armor, toughness, pool, RULES);
        return DamageFormula.remainingDamage(hit, hit.armorReduction(), RULES.maxArmorReduction());
    }

    @Test void fullEquipmentMatchesAgreedExamples() {
        double pool = DamageFormula.capacity(3000, 2000, RULES);
        assertEquals(6000, pool);
        assertEquals(0, damage(6000, 3000, 2000, pool));
        assertEquals(100, damage(7000, 3000, 2000, pool), 1e-9);
        assertEquals(600, damage(6000, 3000, 2000, 0), 1e-9);
    }

    @Test void currentEquipmentMatchesAgreedCapacity() {
        double pool = DamageFormula.capacity(1889, 1889, RULES);
        assertEquals(3673.1605, pool, 1e-9);
        assertEquals(232.68395, damage(6000, 1889, 1889, pool), 1e-8);
    }

    @Test void higherAttributeLimitsDoNotImposeAFixedPoolSize() {
        assertEquals(17500, DamageFormula.capacity(10000, 5000, RULES));
        assertEquals(2_500_000, DamageFormula.capacity(1_000_000, 1_000_000, RULES));
        assertEquals(0, damage(17500, 10000, 5000, 17500));
        assertEquals(250, damage(20000, 10000, 5000, 17500), 1e-8);
    }

    @Test void toughnessNeedsArmorForPoolButHasSmallStandaloneReduction() {
        assertEquals(0, DamageFormula.capacity(0, 2000, RULES));
        assertEquals(95, damage(100, 0, 2000, 0), 1e-9);
        assertEquals(100, damage(100, 0, 0, 0));
    }

    @Test void highArmorNeverMakesNegativeDamage() {
        for (double armor : new double[]{0, 1, 100, 1889, 3000, 1_000_000}) {
            for (double incoming : new double[]{0, 1, 100, 6000, 1_000_000_000}) {
                double remaining = damage(incoming, armor, 2000, 0);
                assertTrue(remaining >= incoming * 0.1 - 1e-6);
                assertTrue(remaining <= incoming);
            }
        }
    }

    @Test void repeatedSmallHitsExhaustPoolAndThenCostHealth() {
        double pool = 6000;
        for (int attack = 0; attack < 60; attack++) {
            var hit = DamageFormula.calculate(100, 3000, 2000, pool, RULES);
            assertEquals(0, DamageFormula.remainingDamage(hit, hit.armorReduction(), 0.9));
            pool -= hit.poolSpent();
        }
        assertEquals(0, pool);
        assertEquals(10, damage(100, 3000, 2000, pool), 1e-9);
    }

    @Test void rejectsNonFiniteInputsBeforeChangingState() {
        assertThrows(IllegalArgumentException.class, () -> damage(Double.NaN, 3000, 2000, 6000));
        assertThrows(IllegalArgumentException.class, () -> DamageFormula.capacity(Double.POSITIVE_INFINITY, 0, RULES));
    }

    @Test void fallbackCannotTurnLargeDamageIntoHealing() {
        assertEquals(400_000, DamageFormula.safeVanillaFallback(2_000_000, 3000, 2000), 1e-6);
        assertEquals(100, DamageFormula.safeVanillaFallback(100, Double.NaN, Double.NaN));
        assertEquals(0, DamageFormula.safeVanillaFallback(Double.NaN, 3000, 2000));
    }
}
