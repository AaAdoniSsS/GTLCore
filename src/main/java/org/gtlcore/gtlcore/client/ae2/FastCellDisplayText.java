package org.gtlcore.gtlcore.client.ae2;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;

/** Immutable text, computed only when the synchronized amount changes. */
public record FastCellDisplayText(BigInteger amount, String small, String large, String full) {

    private static final String[] UNITS = { "", "K", "M", "G", "T", "P", "E", "Z", "Y", "B", "N", "D", "U", "V", "W" };

    public static FastCellDisplayText create(BigInteger amount, int amountPerUnit, String unit) {
        BigDecimal value = new BigDecimal(amount).divide(BigDecimal.valueOf(amountPerUnit));
        return new FastCellDisplayText(amount, compact(value, 4), compact(value, 3),
                NumberFormat.getNumberInstance().format(value) + (unit.isEmpty() ? "" : " " + unit));
    }

    private static String compact(BigDecimal value, int width) {
        int index = Math.max(0, (value.precision() - value.scale() - 1) / 3);
        index = Math.min(index, UNITS.length - 1);
        BigDecimal scaled = value.movePointLeft(index * 3);
        int integers = Math.max(1, scaled.precision() - scaled.scale());
        if (integers + UNITS[index].length() > width && index + 1 < UNITS.length) {
            // The large-font slot has only three characters: 170U becomes .1V, never 0V.
            scaled = scaled.movePointLeft(3);
            index++;
            return scaled.setScale(width - UNITS[index].length() - 1, RoundingMode.DOWN)
                    .toPlainString().substring(1) + UNITS[index];
        }
        int decimals = Math.min(1, Math.max(0, width - integers - UNITS[index].length() - 1));
        return scaled.setScale(decimals, RoundingMode.DOWN).stripTrailingZeros().toPlainString() + UNITS[index];
    }
}
