package kg.tunduk.cvscan.screening.exception;

public class VersionConflictException extends RuntimeException {
    public VersionConflictException(String message) {
        super(message);
    }

    public static VersionConflictException expectedButActual(int expectedVersion, int actualVersion) {
        return new VersionConflictException(
                "Ожидалась версия " + expectedVersion + ", текущая версия " + actualVersion);
    }
}
