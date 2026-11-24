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
package pit12.platform.discord;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

abstract class DiscordIpcTransport implements Closeable {
    long readDeadline;
    final InputStream input = new InputStream() {
        private final byte[] single = new byte[1];

        @Override
        public int read() throws IOException {
            return read(single, 0, 1) < 0 ? -1 : single[0] & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            return length == 0 ? 0 : DiscordIpcTransport.this.read(bytes, offset, length);
        }
    };
    final OutputStream output = new OutputStream() {
        private final byte[] single = new byte[1];

        @Override
        public void write(int value) throws IOException {
            single[0] = (byte) value;
            write(single, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            DiscordIpcTransport.this.write(bytes, offset, length);
        }
    };

    abstract int read(byte[] bytes, int offset, int length) throws IOException;

    abstract void write(byte[] bytes, int offset, int length) throws IOException;

    final long writeDeadline() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        return readDeadline != 0 && deadline - readDeadline > 0 ? readDeadline : deadline;
    }

    static int waitMillis(long deadline, int limit) throws SocketTimeoutException {
        if (deadline == 0) {
            return limit;
        }
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) {
            throw new SocketTimeoutException("Discord IPC timed out");
        }
        return (int) Math.min(limit, TimeUnit.NANOSECONDS.toMillis(nanos - 1) + 1);
    }
}
