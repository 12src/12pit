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
package pit12.shared.result;

public final class OperationResult<T> {
    public enum Status {
        SUCCESS, UNAVAILABLE, INVALID_VALUE, NOT_FOUND
    }

    private final Status status;
    private final String message;
    private final T value;

    private OperationResult(Status status, String message, T value) {
        this.status = status;
        this.message = message == null ? "" : message;
        this.value = value;
    }

    public static <T> OperationResult<T> success(T value) {
        return success(value, "");
    }

    public static <T> OperationResult<T> success(T value, String message) {
        return new OperationResult<>(Status.SUCCESS, message, value);
    }

    public static <T> OperationResult<T> failure(Status status, String message) {
        return new OperationResult<>(status, message, null);
    }

    public boolean succeeded() {
        return status == Status.SUCCESS;
    }

    public Status status() {
        return status;
    }

    public String message() {
        return message;
    }

    public T value() {
        return value;
    }
}
