package kg.tunduk.cvscan.screening.soap;

/**
 * {@code path} — это примерная цепочка элементов-предков (например, {@code /VerifyEducationResponse/result}),
 * построенная из стека открытых элементов SAX-парсера в момент, когда валидатор сообщил об
 * ошибке — не настоящее XPath-выражение, для которого понадобился бы полноценный XPath-движок,
 * что избыточно для такой маленькой схемы.
 */
public record XmlDiagnostic(String path, String message) {
}
