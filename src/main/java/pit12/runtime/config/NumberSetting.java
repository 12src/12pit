/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 The 12pit Authors and contributors <https://github.com/12src/12pit>
 *
 * 12pit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 12pit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with 12pit. If not, see <https://www.gnu.org/licenses/>.
 */
package pit12.runtime.config;

import java.math.BigDecimal;
import java.math.RoundingMode;

public abstract class NumberSetting<T extends Number> extends Setting<T> {
    private final double minimum;
    private final double maximum;
    private final double step;
    private final int decimalPlaces;

    protected NumberSetting(String id, String displayName, String description, T defaultValue,
            StorageType storageType, double minimum, double maximum, double step,
            int decimalPlaces) {
        super(id, displayName, description, defaultValue, storageType);
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.decimalPlaces = decimalPlaces;
    }

    public final double minimumValue() {
        return minimum;
    }

    public final double maximumValue() {
        return maximum;
    }

    public final double stepValue() {
        return step;
    }

    public final int decimalPlaces() {
        return decimalPlaces;
    }

    @Override
    protected final T requireValue(Object candidate) {
        if (!(candidate instanceof Number)) {
            throw new IllegalArgumentException("Setting " + id() + " requires a number");
        }
        double value = ((Number) candidate).doubleValue();
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    "Setting " + id() + " requires a number from " + minimum + " to " + maximum);
        }
        return valueFromDouble(snap(value, minimum, maximum, step));
    }

    protected abstract T valueFromDouble(double value);

    protected static double normalizedDefault(double defaultValue, double minimum, double maximum,
            double step) {
        if (!Double.isFinite(defaultValue) || defaultValue < minimum || defaultValue > maximum) {
            throw new IllegalArgumentException("defaultValue must be within the number range");
        }
        return snap(defaultValue, minimum, maximum, step);
    }

    private static double snap(double value, double minimum, double maximum, double step) {
        BigDecimal decimalValue = BigDecimal.valueOf(value);
        BigDecimal decimalMinimum = BigDecimal.valueOf(minimum);
        BigDecimal decimalMaximum = BigDecimal.valueOf(maximum);
        BigDecimal decimalStep = BigDecimal.valueOf(step);
        BigDecimal steps =
                decimalValue.subtract(decimalMinimum).divide(decimalStep, 0, RoundingMode.HALF_UP);
        BigDecimal snapped = decimalMinimum.add(decimalStep.multiply(steps));
        if (snapped.compareTo(decimalMinimum) < 0) {
            snapped = decimalMinimum;
        } else if (snapped.compareTo(decimalMaximum) > 0) {
            snapped = decimalMaximum;
        }
        BigDecimal distanceToMaximum = decimalMaximum.subtract(decimalValue).abs();
        BigDecimal distanceToSnapped = snapped.subtract(decimalValue).abs();
        return distanceToMaximum.compareTo(distanceToSnapped) <= 0 ? maximum
                : snapped.doubleValue();
    }
}
