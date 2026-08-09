package kg.tunduk.cvscan.screening.exception;

/** Преобразует пути полей Bean Validation с точками/скобками (например, {@code weights[0].key}) в JSON Pointer. */
public final class FieldErrorToPointerMapper {

    private FieldErrorToPointerMapper() {
    }

    public static String toPointer(final String fieldPath) {
        final String pointer = fieldPath.replace('.', '/').replace('[', '/').replace("]", "");
        return pointer.startsWith("/") ? pointer : "/" + pointer;
    }
}
