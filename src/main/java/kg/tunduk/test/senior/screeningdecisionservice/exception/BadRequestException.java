package kg.tunduk.test.senior.screeningdecisionservice.exception;

/** Request-shape errors not covered by Bean Validation (e.g. an unsupported {@code sort} field). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
