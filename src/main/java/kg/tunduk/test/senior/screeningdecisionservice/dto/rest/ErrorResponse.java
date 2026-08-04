package kg.tunduk.test.senior.screeningdecisionservice.dto.rest;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        int status,
        String error,
        String message,
        List<ErrorDetail> details,
        Instant timestamp,
        String path
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(status, error, message, null, Instant.now(), path);
    }

    public static ErrorResponse of(int status, String error, String message, String path, List<ErrorDetail> details) {
        return new ErrorResponse(status, error, message, details, Instant.now(), path);
    }
}
