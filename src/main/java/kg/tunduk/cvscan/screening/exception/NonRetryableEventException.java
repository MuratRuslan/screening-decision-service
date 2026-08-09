package kg.tunduk.cvscan.screening.exception;

import kg.tunduk.cvscan.screening.generated.rest.model.ErrorResponseDetailsInner;

import java.util.List;

/**
 * Событие {@code cv.parsed}, которое структурно или семантически некорректно и никогда не
 * обработается успешно, сколько бы раз его ни повторяли - зарегистрировано в Kafka error
 * handler как non-retryable, чтобы сразу уходить в DLQ recoverer, не тратя попытки retry.
 */
public class NonRetryableEventException extends RuntimeException {

    private final String errorCode;
    private final List<ErrorResponseDetailsInner> details;

    public NonRetryableEventException(String errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public NonRetryableEventException(String errorCode, String message, List<ErrorResponseDetailsInner> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public List<ErrorResponseDetailsInner> getDetails() {
        return details;
    }
}
