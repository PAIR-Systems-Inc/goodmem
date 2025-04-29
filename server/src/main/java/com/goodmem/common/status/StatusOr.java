package com.goodmem.common.status;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/**
 * Represents either a successful result with a value or an error status. This is used to avoid
 * exceptions in normal error handling flow.
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
   *
   * @param value the non-null value to wrap
   * @return a new StatusOr containing the value
   * @throws NullPointerException if value is null
   */
  public static <T> StatusOr<T> ofValue(@Nonnull T value) {
    return new StatusOr<>(Status.ok(), Objects.requireNonNull(value));
  }

  /**
   * Creates a new StatusOr with the given non-OK status and no value.
   *
   * @param status the non-OK status to wrap
   * @return a new StatusOr representing the error
   * @throws IllegalArgumentException if status is OK
   */
  public static <T> StatusOr<T> ofStatus(@Nonnull Status status) {
    if (status.isOk()) {
      throw new IllegalArgumentException("Status must not be OK when using ofStatus");
    }
    return new StatusOr<>(status, null);
  }

  /**
   * Creates a new StatusOr with an INTERNAL status from the given exception.
   *
   * @param throwable the exception that caused the failure
   * @return a new StatusOr representing the error
   */
  public static <T> StatusOr<T> ofException(@Nonnull Throwable throwable) {
    return ofStatus(Status.internal("Exception: " + throwable.getMessage(), throwable));
  }

  /**
   * Creates a StatusOr from an Optional. If the Optional is empty, returns a StatusOr with a
   * NOT_FOUND status. Otherwise, returns a StatusOr with the value.
   *
   * @param optional the Optional to convert
   * @param errorMessage the error message to use if the Optional is empty
   * @return a new StatusOr
   */
  public static <T> StatusOr<T> fromOptional(Optional<T> optional, String errorMessage) {
    return optional
        .map(StatusOr::ofValue)
        .orElseGet(() -> StatusOr.ofStatus(Status.notFound(errorMessage)));
  }

  /** Returns the status. */
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

  /** Returns true if this StatusOr is OK and has a value. */
  public boolean isOk() {
    return status.isOk();
  }

  /** Returns true if this StatusOr is not OK and represents an error. */
  public boolean isNotOk() {
    return !status.isOk();
  }

  /**
   * Maps the value if this StatusOr is OK, otherwise returns a StatusOr with the same error.
   *
   * @param mapper the function to apply to the value
   * @return a new StatusOr with either the mapped value or the original error
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
   * Applies a function that returns a StatusOr to the value if this StatusOr is OK, otherwise
   * returns a StatusOr with the same error.
   *
   * @param mapper the function to apply to the value
   * @return a new StatusOr with either the result of the mapper or the original error
   */
  @Nonnull
  public <U> StatusOr<U> flatMap(@Nonnull Function<T, StatusOr<U>> mapper) {
    if (status.isOk()) {
      return mapper.apply(value);
    } else {
      return StatusOr.ofStatus(status);
    }
  }

  /**
   * Applies a function that might throw to the value if this StatusOr is OK, catching any
   * exceptions and converting them to a StatusOr.
   *
   * @param mapper the function to apply to the value
   * @return a new StatusOr with either the result or an error
   */
  @Nonnull
  public <U> StatusOr<U> mapCatching(@Nonnull ThrowingFunction<T, U> mapper) {
    if (status.isOk()) {
      try {
        U mappedValue = mapper.apply(value);
        return StatusOr.ofValue(mappedValue);
      } catch (Exception e) {
        return StatusOr.ofException(e);
      }
    } else {
      return StatusOr.ofStatus(status);
    }
  }

  /**
   * Returns the value if this StatusOr is OK, otherwise returns the provided defaultValue.
   *
   * @param defaultValue the value to return if this StatusOr is not OK
   * @return the value or the default
   */
  @Nonnull
  public T getOrDefault(@Nonnull T defaultValue) {
    if (status.isOk()) {
      return value;
    } else {
      return defaultValue;
    }
  }

  /**
   * Returns the value if this StatusOr is OK, otherwise returns the result of calling the supplier.
   *
   * @param supplier the supplier to use if this StatusOr is not OK
   * @return the value or the result of the supplier
   */
  @Nonnull
  public T getOrElse(@Nonnull Supplier<T> supplier) {
    if (status.isOk()) {
      return value;
    } else {
      return supplier.get();
    }
  }

  /**
   * Returns the value as an Optional if this StatusOr is OK, otherwise returns an empty Optional.
   */
  @Nonnull
  public Optional<T> asOptional() {
    if (status.isOk()) {
      return Optional.of(value);
    } else {
      return Optional.empty();
    }
  }

  /** A functional interface for methods that might throw an exception. */
  @FunctionalInterface
  public interface ThrowingFunction<T, R> {
    R apply(T t) throws Exception;
  }

  /** Returns a string representation of this StatusOr, showing either the value or the status. */
  @Override
  public String toString() {
    if (status.isOk()) {
      return "StatusOr{value=" + value + "}";
    } else {
      return "StatusOr{status=" + status + "}";
    }
  }

  /** Compares this StatusOr with another object for equality. */
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

  /** Returns a hash code for this StatusOr. */
  @Override
  public int hashCode() {
    return Objects.hash(status, value);
  }
}
