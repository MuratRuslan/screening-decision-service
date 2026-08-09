package kg.tunduk.cvscan.screening.validation.json;

/** Одно нарушение JSON Schema с указанием местоположения через JSON Pointer (например, {@code /criteria/0/key}). */
public record JsonPointerError(String pointer, String message) {
}
