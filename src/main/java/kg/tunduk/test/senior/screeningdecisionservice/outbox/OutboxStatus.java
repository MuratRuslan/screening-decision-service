package kg.tunduk.test.senior.screeningdecisionservice.outbox;

public enum OutboxStatus {
    NEW,
    SENDING,
    SENT,
    FAILED
}
