package kg.tunduk.test.senior.screeningdecisionservice.soap.model;

public record VerifyEducationResponse(String candidateId, EducationVerificationResult result, String message) {
}
