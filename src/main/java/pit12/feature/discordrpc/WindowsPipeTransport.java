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
package pit12.feature.discordrpc;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class WindowsPipeTransport implements Closeable {
    private static final int GENERIC_READ = 0x80000000;
    private static final int GENERIC_WRITE = 0x40000000;
    private static final int OPEN_EXISTING = 3;
    private static final int PIPE_NOWAIT = 1;
    private static final int ERROR_NO_DATA = 232;
    private static final Kernel32 KERNEL32 =
            (Kernel32) Native.loadLibrary("kernel32", Kernel32.class);
    private final Pointer handle;
    private final Memory readBuffer = new Memory(4096);
    private final Memory writeBuffer = new Memory(4096);
    private final IntByReference transferred = new IntByReference();
    private boolean closed;
    final InputStream input = new InputStream() {
        private final byte[] singleByte = new byte[1];

        @Override
        public int read() throws IOException {
            return read(singleByte, 0, 1) < 0 ? -1 : singleByte[0] & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            synchronized (WindowsPipeTransport.this) {
                while (true) {
                    checkOpen();
                    if (KERNEL32.ReadFile(handle, readBuffer,
                            (int) Math.min(length, readBuffer.size()), transferred, null)) {
                        int count = transferred.getValue();
                        if (count == 0) {
                            return -1;
                        }
                        readBuffer.read(0, bytes, offset, count);
                        return count;
                    }
                    int error = Native.getLastError();
                    if (error != ERROR_NO_DATA) {
                        throw new IOException(
                                "Failed to read Discord IPC pipe (Windows error " + error + ")");
                    }
                    awaitData();
                }
            }
        }
    };
    final OutputStream output = new OutputStream() {
        private final byte[] singleByte = new byte[1];

        @Override
        public void write(int value) throws IOException {
            singleByte[0] = (byte) value;
            write(singleByte, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            synchronized (WindowsPipeTransport.this) {
                while (length > 0) {
                    checkOpen();
                    int count = (int) Math.min(length, writeBuffer.size());
                    writeBuffer.write(0, bytes, offset, count);
                    if (!KERNEL32.WriteFile(handle, writeBuffer, count, transferred, null)) {
                        throw new IOException("Failed to write Discord IPC pipe (Windows error "
                                + Native.getLastError() + ")");
                    }
                    int written = transferred.getValue();
                    if (written == 0) {
                        awaitData();
                    } else {
                        offset += written;
                        length -= written;
                    }
                }
            }
        }
    };

    WindowsPipeTransport(String endpoint) throws IOException {
        handle = KERNEL32.CreateFileW(new WString(endpoint), GENERIC_READ | GENERIC_WRITE, 0, null,
                OPEN_EXISTING, 0, null);
        if (Pointer.nativeValue(handle) == (Native.POINTER_SIZE == 8 ? -1L : 0xffffffffL)) {
            throw new IOException("Failed to open Discord IPC pipe: " + endpoint
                    + " (Windows error " + Native.getLastError() + ")");
        }
        // Nonblocking I/O lets shutdown close the pipe without waiting for Discord.
        if (!KERNEL32.SetNamedPipeHandleState(handle, new IntByReference(PIPE_NOWAIT), null,
                null)) {
            int error = Native.getLastError();
            KERNEL32.CloseHandle(handle);
            throw new IOException(
                    "Failed to configure Discord IPC pipe (Windows error " + error + ")");
        }
    }

    private void awaitData() throws IOException {
        try {
            wait(100L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for Discord IPC pipe", interrupted);
        }
    }

    private void checkOpen() throws IOException {
        if (closed) {
            throw new IOException("Discord IPC pipe is closed");
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        notifyAll();
        if (!KERNEL32.CloseHandle(handle)) {
            throw new IOException("Failed to close Discord IPC pipe (Windows error "
                    + Native.getLastError() + ")");
        }
    }

    public interface Kernel32 extends StdCallLibrary {
        Pointer CreateFileW(WString name, int access, int sharing, Pointer security, int creation,
                int flags, Pointer template);

        boolean SetNamedPipeHandleState(Pointer pipe, IntByReference mode, Pointer collectionCount,
                Pointer collectionTimeout);

        boolean ReadFile(Pointer file, Pointer buffer, int length, IntByReference read,
                Pointer overlapped);

        boolean WriteFile(Pointer file, Pointer buffer, int length, IntByReference written,
                Pointer overlapped);

        boolean CloseHandle(Pointer handle);
    }
}
