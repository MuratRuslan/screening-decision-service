package kg.tunduk.cvscan.screening.exception;

/** Ошибки формата запроса, не покрываемые Bean Validation (например, неподдерживаемое поле {@code sort}). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
