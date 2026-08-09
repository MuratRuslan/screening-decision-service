package kg.tunduk.cvscan.screening.validation.json;

/** A single JSON Schema violation, located by JSON Pointer (e.g. {@code /criteria/0/key}). */
public record JsonPointerError(String pointer, String message) {
}
