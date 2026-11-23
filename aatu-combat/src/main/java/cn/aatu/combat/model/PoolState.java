package cn.aatu.combat.model;

/** Immutable snapshots let preview calculations run without consuming protection. */
public record PoolState(double points, long lastHitTick, long lastRecoveryTick) {
    public static final double TICKS_PER_SECOND = 20.0;

    public PoolState advance(double capacity, long now, boolean recoveryEnabled,
                             long delayTicks, double fractionPerSecond) {
        double current = Double.isFinite(points) ? DamageFormula.clamp(points, 0, capacity) : 0;
        if (now < lastRecoveryTick || now < lastHitTick) {
            return new PoolState(current, now, now);
        }
        long eligibleStart = Math.max(lastRecoveryTick, lastHitTick + delayTicks);
        if (recoveryEnabled && now > eligibleStart) {
            current = Math.min(capacity, current + capacity * fractionPerSecond
                    * ((now - eligibleStart) / TICKS_PER_SECOND));
        }
        return new PoolState(current, lastHitTick, now);
    }

    public PoolState hit(double spent, long now) {
        return new PoolState(Math.max(0, points - Math.max(0, spent)), now, now);
    }

    public PoolState heal(double healedHealth, double maximumHealth,
                          double capacity, double multiplier) {
        if (!Double.isFinite(healedHealth) || !Double.isFinite(maximumHealth)
                || healedHealth <= 0 || maximumHealth <= 0) {
            return this;
        }
        double restored = capacity * healedHealth / maximumHealth * multiplier;
        return new PoolState(Math.min(capacity, points + restored), lastHitTick, lastRecoveryTick);
    }
}
