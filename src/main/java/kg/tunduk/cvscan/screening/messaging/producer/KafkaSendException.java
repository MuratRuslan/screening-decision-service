package kg.tunduk.cvscan.screening.messaging.producer;

public class KafkaSendException extends RuntimeException {
    public KafkaSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
