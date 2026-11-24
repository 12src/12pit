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

import com.sun.jna.LastErrorException;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

final class UnixSocketTransport extends DiscordIpcTransport {
    private static final boolean MAC = Platform.isMac();
    private static final int AF_UNIX = 1;
    private static final int EINTR = 4;
    private static final int EAGAIN = MAC ? 35 : 11;
    private static final short POLLOUT = 4;
    private static final int SOL_SOCKET = MAC ? 0xffff : 1;
    private static final LibC LIBC = (LibC) Native.loadLibrary("c", LibC.class);
    // These buffers belong to the worker. Only close can run on the client thread.
    private final Memory buffer = new Memory(8192);
    private final Memory pollDescriptor = new Memory(8);
    private final int descriptor;
    private boolean closed;

    @Override
    int read(byte[] bytes, int offset, int length) throws IOException {
        while (true) {
            waitMillis(readDeadline, 1);
            try {
                synchronized (this) {
                    checkOpen();
                    int count =
                            (int) LIBC
                                    .recv(descriptor, buffer,
                                            new NativeLong(Math.min(length, buffer.size())), 0)
                                    .longValue();
                    if (count == 0) {
                        return -1;
                    }
                    buffer.read(0, bytes, offset, count);
                    return count;
                }
            } catch (LastErrorException failure) {
                if (failure.getErrorCode() == EINTR) {
                    continue;
                }
                if (failure.getErrorCode() != EAGAIN) {
                    throw new IOException("Failed to read Discord IPC socket", failure);
                }
            }
            awaitReady((short) 1, readDeadline);
        }
    }

    @Override
    void write(byte[] bytes, int offset, int length) throws IOException {
        long deadline = writeDeadline();
        while (length > 0) {
            waitMillis(deadline, 1);
            int count = (int) Math.min(length, buffer.size());
            buffer.write(0, bytes, offset, count);
            try {
                synchronized (this) {
                    checkOpen();
                    // Suppress SIGPIPE so writing to a closed socket gives us a Java error.
                    count = (int) LIBC
                            .send(descriptor, buffer, new NativeLong(count), MAC ? 0 : 0x4000)
                            .longValue();
                }
                if (count == 0) {
                    throw new IOException("Discord IPC socket wrote no data");
                }
                offset += count;
                length -= count;
            } catch (LastErrorException failure) {
                if (failure.getErrorCode() == EINTR) {
                    continue;
                }
                if (failure.getErrorCode() != EAGAIN) {
                    throw new IOException("Failed to write Discord IPC socket", failure);
                }
                awaitReady(POLLOUT, deadline);
            }
        }
    }

    @SuppressWarnings({"PMD.AvoidCatchingGenericException",
            "PMD.AvoidInstanceofChecksInCatchClause"})
    UnixSocketTransport(File endpoint) throws IOException {
        // TODO: BSD socket layouts. Only Linux and macOS work here for now.
        if (!MAC && !Platform.isLinux()) {
            throw new IOException("Discord IPC sockets require Linux or macOS");
        }
        byte[] path = endpoint.getPath().getBytes(StandardCharsets.UTF_8);
        Memory address = new Memory(MAC ? 106 : 110);
        if (path.length >= address.size() - 2) {
            throw new IOException("Discord IPC socket path is too long: " + endpoint);
        }
        address.clear();
        // sockaddr_un has a length byte on macOS and a two-byte family on Linux.
        if (MAC) {
            address.setByte(0, (byte) address.size());
            address.setByte(1, (byte) AF_UNIX);
        } else {
            address.setShort(0, (short) AF_UNIX);
        }
        address.write(2, path, 0, path.length);
        try {
            descriptor = LIBC.socket(AF_UNIX, 1, 0);
        } catch (LastErrorException failure) {
            throw new IOException("Failed to create Discord IPC socket", failure);
        }
        try {
            pollDescriptor.setInt(0, descriptor);
            // FD_CLOEXEC stops child processes from inheriting the socket.
            LIBC.fcntl(descriptor, 2, 1);
            // recv and send hold the monitor. Don't let them block close on the client thread.
            LIBC.fcntl(descriptor, 4, MAC ? 4 : 0x800);
            if (MAC) {
                LIBC.setsockopt(descriptor, SOL_SOCKET, 0x1022, new IntByReference(1), 4);
            }
            try {
                LIBC.connect(descriptor, address, (int) address.size());
            } catch (LastErrorException failure) {
                if (failure.getErrorCode() != (MAC ? 36 : 115)) {
                    throw new IOException("Failed to connect Discord IPC socket: " + endpoint,
                            failure);
                }
                awaitReady(POLLOUT, System.nanoTime() + TimeUnit.SECONDS.toNanos(2));
                // A failed connection can also look writable. Check SO_ERROR to tell the difference.
                IntByReference error = new IntByReference();
                LIBC.getsockopt(descriptor, SOL_SOCKET, MAC ? 0x1007 : 4, error,
                        new IntByReference(4));
                if (error.getValue() != 0) {
                    throw new IOException("Failed to connect Discord IPC socket: " + endpoint
                            + " (errno " + error.getValue() + ")", failure);
                }
            }
        } catch (IOException | RuntimeException | Error failure) {
            try {
                close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            if (failure instanceof LastErrorException) {
                throw new IOException("Failed to open Discord IPC socket: " + endpoint, failure);
            }
            throw failure;
        }
    }

    private void awaitReady(short events, long deadline) throws IOException {
        pollDescriptor.setShort(4, events);
        while (true) {
            synchronized (this) {
                checkOpen();
            }
            int remaining = waitMillis(deadline, 1000);
            // poll can finish after close. recv and send check closed again before using the descriptor.
            try {
                int ready = MAC ? LIBC.poll(pollDescriptor, 1, remaining)
                        : LIBC.poll(pollDescriptor, new NativeLong(1), remaining);
                if (ready > 0) {
                    return;
                }
            } catch (LastErrorException failure) {
                if (failure.getErrorCode() != EINTR) {
                    throw new IOException("Failed to wait for Discord IPC socket", failure);
                }
            }
        }
    }

    private void checkOpen() throws IOException {
        if (closed) {
            throw new IOException("Discord IPC socket is closed");
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            LIBC.close(descriptor);
        } catch (LastErrorException failure) {
            throw new IOException("Failed to close Discord IPC socket", failure);
        }
    }

    // LastErrorException tells JNA to capture native errors.
    @SuppressWarnings("PMD.AvoidUncheckedExceptionsInSignatures")
    public interface LibC extends Library {
        int socket(int domain, int type, int protocol) throws LastErrorException;

        void fcntl(int descriptor, int command, Object... arguments) throws LastErrorException;

        void connect(int descriptor, Pointer address, int length) throws LastErrorException;

        void setsockopt(int descriptor, int level, int option, IntByReference value, int length)
                throws LastErrorException;

        void getsockopt(int descriptor, int level, int option, IntByReference value,
                IntByReference length) throws LastErrorException;

        // size_t and ssize_t use native long on Linux and macOS.
        NativeLong recv(int descriptor, Pointer buffer, NativeLong length, int flags)
                throws LastErrorException;

        NativeLong send(int descriptor, Pointer buffer, NativeLong length, int flags)
                throws LastErrorException;

        // nfds_t uses int on macOS and native long on Linux.
        int poll(Pointer descriptors, int count, int timeout) throws LastErrorException;

        int poll(Pointer descriptors, NativeLong count, int timeout) throws LastErrorException;

        int close(int descriptor) throws LastErrorException;
    }
}
