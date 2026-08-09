package kg.tunduk.cvscan.screening.exception;

public class VersionConflictException extends RuntimeException {
    public VersionConflictException(final String message) {
        super(message);
    }

    public static VersionConflictException expectedButActual(final int expectedVersion, final int actualVersion) {
        return new VersionConflictException(
                "Ожидалась версия " + expectedVersion + ", текущая версия " + actualVersion);
    }
}
