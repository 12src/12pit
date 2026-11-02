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
package pit12.shared.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class DemandLifecycleTest {
    @Test
    public void startupWaitsForDemandAndRebuildsAfterIdle() {
        Service service = new Service();
        Object owner = new Object();
        service.start();
        assertEquals(0, service.activations);
        service.retain(owner);
        assertEquals(1, service.activations);
        service.release(owner);
        assertEquals(1, service.deactivations);
        service.retain(owner);
        assertEquals(2, service.activations);
        service.stop();
        service.stop();
        assertEquals(2, service.deactivations);
    }

    @Test
    public void retainingTheSameOwnerTwiceNeedsOnlyOneRelease() {
        Service service = new Service();
        Object owner = new Object();
        service.start();
        service.retain(owner);
        service.retain(owner);
        assertEquals(1, service.activations);
        service.release(owner);
        assertEquals(1, service.deactivations);
        service.stop();
    }

    @Test
    public void sharedOwnersKeepTheServiceAlive() {
        Service service = new Service();
        Object first = new String("owner");
        Object second = new String("owner");
        service.retain(first);
        service.retain(first);
        service.start();
        service.retain(second);
        service.release(first);
        service.release(first);
        assertEquals(1, service.activations);
        assertEquals(0, service.deactivations);
        service.release(second);
        assertEquals(1, service.deactivations);
    }

    @Test
    public void theLastConsumerAlsoReleasesUpstreamResources() {
        Service upstream = new Service();
        DemandLifecycle first = dependent(upstream);
        DemandLifecycle second = dependent(upstream);
        Object owner = new Object();
        upstream.start();
        first.start();
        second.start();
        first.retain(owner);
        second.retain(owner);
        assertEquals(1, upstream.activations);
        first.release(owner);
        assertEquals(0, upstream.deactivations);
        second.release(owner);
        assertEquals(1, upstream.deactivations);
        first.stop();
        second.stop();
        upstream.stop();
        assertEquals(1, upstream.deactivations);
    }

    @Test
    public void stopCleansUpAPartialActivationAndClearsItsOwners() {
        Service upstream = new Service();
        DemandLifecycle service = new DemandLifecycle(() -> {
        }) {
            @Override
            protected void activate() {
                upstream.retain(this);
                throw new IllegalStateException("Activation failed");
            }

            @Override
            protected void deactivate() {
                upstream.release(this);
            }
        };
        upstream.start();
        service.start();
        try {
            service.retain(new Object());
            fail("Activation should fail");
        } catch (IllegalStateException expected) {
            assertEquals("Activation failed", expected.getMessage());
        }
        service.stop();
        service.stop();
        service.start();
        assertEquals(1, upstream.activations);
        assertEquals(1, upstream.deactivations);
        service.stop();
        upstream.stop();
    }

    private static DemandLifecycle dependent(Service upstream) {
        return new DemandLifecycle(() -> {
        }) {
            @Override
            protected void activate() {
                upstream.retain(this);
            }

            @Override
            protected void deactivate() {
                upstream.release(this);
            }
        };
    }

    private static final class Service extends DemandLifecycle {
        int activations;
        int deactivations;

        Service() {
            super(() -> {
            });
        }

        @Override
        protected void activate() {
            activations++;
        }

        @Override
        protected void deactivate() {
            deactivations++;
        }
    }
}
