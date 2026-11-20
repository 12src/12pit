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
package pit12.runtime.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public final class SessionStateTest {
    private final SessionState<Object, Object> session = new SessionState<>();

    @Test
    public void ignoresAnUnloadedWorldUntilMinecraftChangesItsReference() {
        Object connection = new Object();
        Object oldWorld = new Object();
        Object newWorld = new Object();
        session.update(connection, oldWorld);
        session.unload(oldWorld);
        long revision = session.revision();
        session.update(connection, oldWorld);
        assertSame(connection, session.connection());
        assertNull(session.world());
        assertEquals(revision, session.revision());
        session.update(connection, newWorld);
        assertSame(newWorld, session.world());
    }

    @Test
    public void aLateDisconnectLeavesTheNewConnectionAndWorldIntact() {
        Object oldConnection = new Object();
        Object newConnection = new Object();
        Object newWorld = new Object();
        session.update(oldConnection, new Object());
        session.update(newConnection, newWorld);
        long revision = session.revision();
        session.disconnect(oldConnection);
        assertSame(newConnection, session.connection());
        assertSame(newWorld, session.world());
        assertEquals(revision, session.revision());
    }

    @Test
    public void disconnectBlocksStaleWorldEvenAfterNewConnectionArrives() {
        Object oldConnection = new Object();
        Object newConnection = new Object();
        Object staleWorld = new Object();
        Object freshWorld = new Object();
        session.update(oldConnection, staleWorld);
        session.disconnect(oldConnection);
        session.update(newConnection, staleWorld);
        assertSame(newConnection, session.connection());
        assertNull(session.world());
        session.update(newConnection, freshWorld);
        assertSame(freshWorld, session.world());
    }

    @Test
    public void clearResetsBlockedReferences() {
        Object connection = new Object();
        Object world = new Object();
        session.update(connection, world);
        session.disconnect(connection);
        session.clear();
        session.update(connection, world);
        assertSame(connection, session.connection());
        assertSame(world, session.world());
    }
}
