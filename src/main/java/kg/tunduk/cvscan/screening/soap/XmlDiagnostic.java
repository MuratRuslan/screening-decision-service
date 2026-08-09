package kg.tunduk.cvscan.screening.soap;

/**
 * {@code path} is a best-effort element ancestor chain (e.g. {@code /VerifyEducationResponse/result}),
 * built from the SAX parser's open-element stack at the moment the validator reported the
 * error - not a true XPath expression, which would need a full XPath engine for a scope this small.
 */
public record XmlDiagnostic(String path, String message) {
}
