package org.gtlcore.gtlcore.api.machine.computation;

import java.math.BigInteger;

/** Non-negative arithmetic at the legacy int capability boundary. */
public final class ComputationMath {

    private ComputationMath() {}

    public static long add(long a, long b) {
        if (a < 0 || b < 0) throw new IllegalArgumentException("Negative computation");
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    public static int toInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, value));
    }

    public static long multiplyDivide(long a, long b, long divisor) {
        if (a < 0 || b < 0 || divisor <= 0) throw new IllegalArgumentException("Invalid computation cost");
        if (a == 0 || b == 0) return 0;
        if (a <= Long.MAX_VALUE / b) return a * b / divisor;
        return new BigInteger(Long.toString(a)).multiply(BigInteger.valueOf(b))
                .divide(BigInteger.valueOf(divisor)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
    }
}
