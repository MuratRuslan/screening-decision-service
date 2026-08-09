package kg.tunduk.cvscan.screening.precheck;

public record PrecheckResult(String name, PrecheckStatus status, String detail, long durationMs) {

    public PrecheckResult withDuration(final long durationMs) {
        return new PrecheckResult(name, status, detail, durationMs);
    }
}
