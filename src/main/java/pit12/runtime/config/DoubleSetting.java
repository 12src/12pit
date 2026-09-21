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

public final class DoubleSetting extends NumberSetting<Double> {
    DoubleSetting(String id, String displayName, String description, double defaultValue,
            double minimum, double maximum, double step) {
        super(id, displayName, description,
                normalizedDefaultValue(defaultValue, minimum, maximum, step), StorageType.DECIMAL,
                minimum, maximum, step, decimalPlaces(minimum, maximum, step));
    }

    public double minimum() {
        return minimumValue();
    }

    public double maximum() {
        return maximumValue();
    }

    public double step() {
        return stepValue();
    }

    @Override
    protected Double valueFromDouble(double value) {
        return Double.valueOf(BigDecimal.valueOf(value)
                .setScale(decimalPlaces(), RoundingMode.HALF_UP).doubleValue());
    }

    private static Double normalizedDefaultValue(double defaultValue, double minimum,
            double maximum, double step) {
        double normalized = normalizedDefault(defaultValue, minimum, maximum, step);
        return Double.valueOf(BigDecimal.valueOf(normalized)
                .setScale(decimalPlaces(minimum, maximum, step), RoundingMode.HALF_UP)
                .doubleValue());
    }

    private static int decimalPlaces(double minimum, double maximum, double step) {
        return Math.max(decimalPlaces(minimum),
                Math.max(decimalPlaces(maximum), decimalPlaces(step)));
    }

    private static int decimalPlaces(double value) {
        return Math.max(0, BigDecimal.valueOf(value).stripTrailingZeros().scale());
    }
}
