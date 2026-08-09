package kg.tunduk.cvscan.screening.soap.model;

public record VerifyEducationResponse(String candidateId, EducationVerificationResult result, String message) {
}
