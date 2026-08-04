package kg.tunduk.test.senior.screeningdecisionservice.precheck;

import kg.tunduk.test.senior.screeningdecisionservice.dto.kafka.CvParsedEvent;
import kg.tunduk.test.senior.screeningdecisionservice.soap.EducationVerificationOutcome;
import kg.tunduk.test.senior.screeningdecisionservice.soap.SoapEducationAdapter;
import org.springframework.stereotype.Component;

@Component
public class EducationFormatCheck implements PrecheckCheck {

    private final SoapEducationAdapter soapEducationAdapter;

    public EducationFormatCheck(SoapEducationAdapter soapEducationAdapter) {
        this.soapEducationAdapter = soapEducationAdapter;
    }

    @Override
    public String name() {
        return "education-format-check";
    }

    @Override
    public PrecheckResult run(CvParsedEvent event) {
        EducationVerificationOutcome outcome = soapEducationAdapter.verify(
                event.candidateId(), event.name(), event.education());

        if (!outcome.valid()) {
            String detail = outcome.errorCode() + ": " + outcome.diagnostics();
            return new PrecheckResult(name(), PrecheckStatus.FAILED, detail, 0);
        }

        return switch (outcome.result()) {
            case VERIFIED -> new PrecheckResult(name(), PrecheckStatus.PASSED, outcome.message(), 0);
            case NEEDS_REVIEW -> new PrecheckResult(name(), PrecheckStatus.WARNING, outcome.message(), 0);
            case INVALID_FORMAT -> new PrecheckResult(name(), PrecheckStatus.FAILED, outcome.message(), 0);
        };
    }
}
