package kg.tunduk.cvscan.screening.soap;

import kg.tunduk.cvscan.screening.soap.model.EducationVerificationResult;

import java.util.List;

public record EducationVerificationOutcome(
        boolean valid,
        EducationVerificationResult result,
        String message,
        String errorCode,
        List<XmlDiagnostic> diagnostics
) {
    public static EducationVerificationOutcome success(EducationVerificationResult result, String message) {
        return new EducationVerificationOutcome(true, result, message, null, List.of());
    }

    public static EducationVerificationOutcome invalid(String errorCode, List<XmlDiagnostic> diagnostics) {
        return new EducationVerificationOutcome(false, null, null, errorCode, diagnostics);
    }
}
