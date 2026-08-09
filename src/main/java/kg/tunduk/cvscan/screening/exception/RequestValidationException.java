package kg.tunduk.cvscan.screening.exception;

import kg.tunduk.cvscan.screening.generated.rest.model.ErrorResponseDetailsInner;

import java.util.List;

/** 400 VALIDATION_ERROR for checks that need application state, not just Bean Validation. */
public class RequestValidationException extends RuntimeException {

    private final List<ErrorResponseDetailsInner> details;

    public RequestValidationException(String message, List<ErrorResponseDetailsInner> details) {
        super(message);
        this.details = details;
    }

    public List<ErrorResponseDetailsInner> getDetails() {
        return details;
    }
}
