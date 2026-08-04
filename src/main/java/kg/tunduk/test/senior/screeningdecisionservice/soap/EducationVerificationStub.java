package kg.tunduk.test.senior.screeningdecisionservice.soap;

import kg.tunduk.test.senior.screeningdecisionservice.soap.model.EducationVerificationResult;
import kg.tunduk.test.senior.screeningdecisionservice.soap.model.VerifyEducationRequest;
import kg.tunduk.test.senior.screeningdecisionservice.soap.model.VerifyEducationResponse;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Stands in for the real external education-verification SOAP service, which TASK.md says
 * is out of scope to actually build. Genuinely round-trips through XML strings (not domain
 * objects) so it exercises the codec/XSD path the way a real remote call would.
 */
@Component
public class EducationVerificationStub {

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");

    private final EducationVerificationXmlCodec codec;

    public EducationVerificationStub(EducationVerificationXmlCodec codec) {
        this.codec = codec;
    }

    public String handle(String requestXml) {
        simulateNetworkLatency();

        VerifyEducationRequest request = codec.unmarshalRequest(requestXml);
        EducationVerificationResult result = evaluate(request.educationText());
        VerifyEducationResponse response = new VerifyEducationResponse(request.candidateId(), result, messageFor(result));
        return codec.marshalResponse(response);
    }

    private EducationVerificationResult evaluate(String educationText) {
        if (educationText == null || educationText.isBlank()) {
            return EducationVerificationResult.INVALID_FORMAT;
        }
        boolean hasYear = YEAR_PATTERN.matcher(educationText).find();
        boolean hasSubstantiveText = educationText.trim().length() >= 8;
        if (hasYear && hasSubstantiveText) {
            return EducationVerificationResult.VERIFIED;
        }
        if (hasSubstantiveText) {
            return EducationVerificationResult.NEEDS_REVIEW;
        }
        return EducationVerificationResult.INVALID_FORMAT;
    }

    private String messageFor(EducationVerificationResult result) {
        return switch (result) {
            case VERIFIED -> "Образование подтверждено по формату (найден год и текст)";
            case NEEDS_REVIEW -> "Формат неполный, требуется ручная проверка";
            case INVALID_FORMAT -> "Поле education отсутствует или слишком короткое";
        };
    }

    private void simulateNetworkLatency() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(30, 120));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
