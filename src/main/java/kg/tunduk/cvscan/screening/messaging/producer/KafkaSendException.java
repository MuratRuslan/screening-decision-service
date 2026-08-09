package kg.tunduk.cvscan.screening.messaging.producer;

public class KafkaSendException extends RuntimeException {
    public KafkaSendException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
