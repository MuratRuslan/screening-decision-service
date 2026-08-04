package kg.tunduk.test.senior.screeningdecisionservice.messaging.producer;

public class KafkaSendException extends RuntimeException {
    public KafkaSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
