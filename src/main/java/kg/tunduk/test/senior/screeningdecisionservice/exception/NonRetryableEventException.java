package kg.tunduk.test.senior.screeningdecisionservice.exception;

import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.ErrorDetail;

import java.util.List;

/**
 * A {@code cv.parsed} event that is structurally or semantically invalid and will never
 * succeed no matter how many times it is retried - registered with the Kafka error handler
 * as non-retryable so it skips straight to the DLQ recoverer instead of wasting retry attempts.
 */
public class NonRetryableEventException extends RuntimeException {

    private final String errorCode;
    private final List<ErrorDetail> details;

    public NonRetryableEventException(String errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public NonRetryableEventException(String errorCode, String message, List<ErrorDetail> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public List<ErrorDetail> getDetails() {
        return details;
    }
}
