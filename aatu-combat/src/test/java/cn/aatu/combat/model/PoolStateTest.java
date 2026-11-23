package cn.aatu.combat.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PoolStateTest {
    private PoolState advance(PoolState state, long now) {
        return state.advance(6000, now, true, 60, 0.05);
    }

    @Test void waitsThreeSecondsThenRestoresFivePercentPerSecond() {
        PoolState empty = new PoolState(0, 100, 100);
        assertEquals(0, advance(empty, 159).points());
        assertEquals(0, advance(empty, 160).points());
        assertEquals(300, advance(empty, 180).points());
        assertEquals(6000, advance(empty, 560).points());
    }

    @Test void absorbedHitRestartsCooldown() {
        PoolState state = advance(new PoolState(3000, 100, 100), 180).hit(100, 180);
        assertEquals(3200, advance(state, 240).points());
        assertEquals(3500, advance(state, 260).points());
    }

    @Test void incrementalReadsDoNotLoseOrDuplicateRecovery() {
        PoolState initial = new PoolState(0, 0, 0);
        PoolState state = initial;
        for (int tick = 1; tick <= 160; tick++) state = advance(state, tick);
        assertEquals(1500, state.points());
        assertEquals(advance(initial, 160).points(), state.points());
    }

    @Test void healingUsesActualFractionAndCombinesWithIdleRecovery() {
        PoolState state = advance(new PoolState(0, 0, 0), 80);
        assertEquals(300, state.points());
        state = state.heal(2, 20, 6000, 1);
        assertEquals(900, state.points());
        assertEquals(1200, advance(state, 100).points());
        assertEquals(state, state.heal(0, 20, 6000, 1));
    }

    @Test void capacityDropClampsAndClockRollbackDoesNotGeneratePoints() {
        PoolState state = new PoolState(5000, 100, 100);
        state = state.advance(1000, 100, true, 60, 0.05);
        assertEquals(1000, state.points());
        assertEquals(1000, state.advance(6000, 100, true, 60, 0.05).points());
        assertEquals(1000, state.advance(6000, 50, true, 60, 0.05).points());
    }

    @Test void disabledRecoveryStaysEmptyAndHealingCannotOverfill() {
        PoolState state = new PoolState(0, 0, 0);
        assertEquals(0, state.advance(6000, 10000, false, 60, 0.05).points());
        assertEquals(6000, state.heal(40, 20, 6000, 1).points());
    }
}
