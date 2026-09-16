/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 12dev <https://github.com/12src>
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
package pit12.bootstrap;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import pit12.feature.Feature;

public final class ClientBootstrap {
    private static final Logger LOGGER = Logger.getLogger(ClientBootstrap.class.getName());
    private final List<Feature> features = new ArrayList<Feature>();
    private final List<Feature> startedFeatures = new ArrayList<Feature>();
    private boolean started;

    public ClientBootstrap() {}

    /**
     * Starts all configured features in order. Calling it again after a successful start has no effect. If a feature throws
     * a {@link RuntimeException}, every feature attempted so far is stopped before the exception is rethrown.
     */
    public synchronized void start() {
        if (started) {
            return;
        }
        try {
            for (Feature feature : features) {
                // Track the feature first so rollback can clean up a partial start.
                startedFeatures.add(feature);
                feature.start();
            }
            started = true;
        } catch (RuntimeException failure) {
            stopStartedFeatures();
            throw failure;
        }
    }

    /**
     * Stops features in reverse order so each one remains available until anything using it has stopped. Cleanup continues
     * if a feature throws a {@link RuntimeException}; the exception is logged instead of being passed to the caller.
     */
    public synchronized void stop() {
        stopStartedFeatures();
        started = false;
    }

    private void stopStartedFeatures() {
        for (int index = startedFeatures.size() - 1; index >= 0; index--) {
            Feature feature = startedFeatures.get(index);
            try {
                feature.stop();
            } catch (RuntimeException failure) {
                LOGGER.log(Level.WARNING, "Failed to stop feature " + feature.getClass().getName(),
                        failure);
            }
        }
        startedFeatures.clear();
    }
}
