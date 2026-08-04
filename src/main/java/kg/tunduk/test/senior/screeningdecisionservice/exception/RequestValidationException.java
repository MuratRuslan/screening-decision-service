package kg.tunduk.test.senior.screeningdecisionservice.exception;

import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.ErrorDetail;

import java.util.List;

/** 400 VALIDATION_ERROR for checks that need application state, not just Bean Validation. */
public class RequestValidationException extends RuntimeException {

    private final List<ErrorDetail> details;

    public RequestValidationException(String message, List<ErrorDetail> details) {
        super(message);
        this.details = details;
    }

    public List<ErrorDetail> getDetails() {
        return details;
    }
}
