package kg.tunduk.test.senior.screeningdecisionservice.precheck;

public record PrecheckResult(String name, PrecheckStatus status, String detail, long durationMs) {

    public PrecheckResult withDuration(long durationMs) {
        return new PrecheckResult(name, status, detail, durationMs);
    }
}
