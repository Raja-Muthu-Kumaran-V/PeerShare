package peershare.util;

import java.util.function.Consumer;

/**
 * Generic success/failure wrapper for the outcome of a background operation
 * (a download, a directory scan, a database query...). Used so
 * Background tasks in the GUI have one consistent, typed
 * way to report back either a value of type {@code T} or an error message,
 * instead of throwing exceptions across the background/UI boundary or
 * returning null and hoping the caller checks.
 */
public final class Result<T> {

    private final T value;
    private final String errorMessage;

    private Result(T value, String errorMessage) {
        this.value = value;
        this.errorMessage = errorMessage;
    }

    public static <T> Result<T> ok(T value) {
        return new Result<>(value, null);
    }

    public static <T> Result<T> fail(String errorMessage) {
        return new Result<>(null, errorMessage);
    }

    public boolean isOk() { return errorMessage == null; }
    public T getValue() { return value; }
    public String getErrorMessage() { return errorMessage; }

    /** Runs {@code onSuccess} if this is a success, otherwise {@code onFailure} with the error message. */
    public void handle(Consumer<T> onSuccess, Consumer<String> onFailure) {
        if (isOk()) onSuccess.accept(value);
        else onFailure.accept(errorMessage);
    }
}
