package com.goodmem.db.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Function;

/**
 * Represents either a successful result with a value or an error status.
 * This is used to avoid exceptions in normal error handling flow.
 *
 * @param <T> The type of the value in case of success.
 */
public final class StatusOr<T> {
    private final Status status;
    private final T value;

    private StatusOr(Status status, T value) {
        if (status.isOk() && value == null) {
            throw new IllegalArgumentException("Value cannot be null when status is OK");
        }
        if (!status.isOk() && value != null) {
            throw new IllegalArgumentException("Value must be null when status is not OK");
        }
        this.status = Objects.requireNonNull(status);
        this.value = value;
    }

    /**
     * Creates a new StatusOr with the given value and an OK status.
     */
    public static <T> StatusOr<T> ofValue(@Nonnull T value) {
        return new StatusOr<>(Status.ok(), Objects.requireNonNull(value));
    }

    /**
     * Creates a new StatusOr with the given non-OK status and no value.
     */
    public static <T> StatusOr<T> ofStatus(@Nonnull Status status) {
        if (status.isOk()) {
            throw new IllegalArgumentException("Status must not be OK when using ofStatus");
        }
        return new StatusOr<>(status, null);
    }

    /**
     * Creates a new StatusOr with an INTERNAL status from the given exception.
     */
    public static <T> StatusOr<T> ofException(@Nonnull Throwable throwable) {
        return ofStatus(Status.internal("Exception: " + throwable.getMessage(), throwable));
    }

    /**
     * Returns the status.
     */
    @Nonnull
    public Status getStatus() {
        return status;
    }

    /**
     * Returns the value if this StatusOr is OK, otherwise throws an IllegalStateException.
     *
     * @throws IllegalStateException if the status is not OK
     */
    @Nonnull
    public T getValue() {
        if (!status.isOk()) {
            throw new IllegalStateException("Cannot get value from failed StatusOr: " + status);
        }
        return value;
    }

    /**
     * Returns true if this StatusOr is OK and has a value.
     */
    public boolean isOk() {
        return status.isOk();
    }

    /**
     * Returns true if this StatusOr is not OK and represents an error.
     */
    public boolean isNotOk() {
        return !status.isOk();
    }

    /**
     * Maps the value if this StatusOr is OK, otherwise returns a StatusOr with the same error.
     */
    @Nonnull
    public <U> StatusOr<U> map(@Nonnull Function<T, U> mapper) {
        if (status.isOk()) {
            U mappedValue = mapper.apply(value);
            return StatusOr.ofValue(mappedValue);
        } else {
            return StatusOr.ofStatus(status);
        }
    }

    /**
     * Returns a string representation of this StatusOr, showing either the value or the status.
     */
    @Override
    public String toString() {
        if (status.isOk()) {
            return "StatusOr{value=" + value + "}";
        } else {
            return "StatusOr{status=" + status + "}";
        }
    }

    /**
     * Compares this StatusOr with another object for equality.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        StatusOr<?> other = (StatusOr<?>) obj;
        return Objects.equals(status, other.status) && Objects.equals(value, other.value);
    }

    /**
     * Returns a hash code for this StatusOr.
     */
    @Override
    public int hashCode() {
        return Objects.hash(status, value);
    }
}