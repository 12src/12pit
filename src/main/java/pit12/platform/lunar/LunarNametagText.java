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
package pit12.platform.lunar;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class LunarNametagText {
    private static final Object EMPTY;
    private static final MethodHandle DESERIALIZE;
    private static final MethodHandle APPEND;
    static {
        try {
            ClassLoader loader = LunarNametagText.class.getClassLoader();
            Class<?> component = Class.forName("com.moonsworth.lunar.lib.adventure.text.Component",
                    false, loader);
            Class<?> serializer = Class.forName(
                    "com.moonsworth.lunar.lib.adventure.text.serializer.legacy.LegacyComponentSerializer",
                    false, loader);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            EMPTY = component.getMethod("empty").invoke(null);
            DESERIALIZE = lookup.unreflect(serializer.getMethod("deserialize", String.class))
                    .bindTo(serializer.getMethod("legacySection").invoke(null))
                    .asType(MethodType.methodType(Object.class, String.class));
            APPEND = lookup
                    .findVirtual(component, "append", MethodType.methodType(component, component))
                    .asType(MethodType.methodType(Object.class, Object.class, Object.class));
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private LunarNametagText() {}

    public static Object append(Object original, String suffix) {
        try {
            // Give the name and suffix their own styles.
            Object name = APPEND.invokeExact(EMPTY, original);
            Object text = DESERIALIZE.invokeExact(suffix);
            return APPEND.invokeExact(name, text);
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not add the Lunar nametag suffix", failure);
        }
    }
}
