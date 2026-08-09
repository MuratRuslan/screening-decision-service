package kg.tunduk.cvscan.screening.soap;

import kg.tunduk.cvscan.screening.soap.model.VerifyEducationRequest;
import kg.tunduk.cvscan.screening.soap.model.VerifyEducationResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Orchestrates: marshal request -> validate against XSD -> "call" the stub -> validate the
 * response against XSD -> unmarshal. Every exception is caught and converted to a failed
 * {@link EducationVerificationOutcome} - this adapter never throws, since it is called from
 * inside a virtual-thread precheck whose failure must not be allowed to affect the rest of
 * the pipeline.
 */
@Component
public class SoapEducationAdapter {

    private final EducationVerificationXmlCodec codec;
    private final EducationVerificationXsdValidator xsdValidator;
    private final EducationVerificationStub stub;

    public SoapEducationAdapter(EducationVerificationXmlCodec codec,
                                 EducationVerificationXsdValidator xsdValidator,
                                 EducationVerificationStub stub) {
        this.codec = codec;
        this.xsdValidator = xsdValidator;
        this.stub = stub;
    }

    public EducationVerificationOutcome verify(String candidateId, String fullName, String educationText) {
        try {
            VerifyEducationRequest request = new VerifyEducationRequest(
                    candidateId, fullName, educationText == null ? "" : educationText);

            String requestXml = codec.marshalRequest(request);
            List<XmlDiagnostic> requestErrors = xsdValidator.validate(requestXml);
            if (!requestErrors.isEmpty()) {
                return EducationVerificationOutcome.invalid("REQUEST_XSD_VIOLATION", requestErrors);
            }

            String responseXml = stub.handle(requestXml);
            List<XmlDiagnostic> responseErrors = xsdValidator.validate(responseXml);
            if (!responseErrors.isEmpty()) {
                return EducationVerificationOutcome.invalid("RESPONSE_XSD_VIOLATION", responseErrors);
            }

            VerifyEducationResponse response = codec.unmarshalResponse(responseXml);
            return EducationVerificationOutcome.success(response.result(), response.message());
        } catch (Exception e) {
            return EducationVerificationOutcome.invalid("ADAPTER_ERROR", List.of(new XmlDiagnostic("/", e.getMessage())));
        }
    }
}
