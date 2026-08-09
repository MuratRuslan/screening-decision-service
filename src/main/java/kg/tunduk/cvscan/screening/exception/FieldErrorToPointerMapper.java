package kg.tunduk.cvscan.screening.exception;

/** Converts Spring's dotted/bracketed Bean Validation field paths (e.g. {@code weights[0].key}) into JSON Pointers. */
public final class FieldErrorToPointerMapper {

    private FieldErrorToPointerMapper() {
    }

    public static String toPointer(String fieldPath) {
        String pointer = fieldPath.replace('.', '/').replace('[', '/').replace("]", "");
        return pointer.startsWith("/") ? pointer : "/" + pointer;
    }
}
