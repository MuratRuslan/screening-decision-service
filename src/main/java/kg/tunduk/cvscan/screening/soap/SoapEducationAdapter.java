package kg.tunduk.cvscan.screening.soap;

import kg.tunduk.cvscan.screening.soap.model.VerifyEducationRequest;
import kg.tunduk.cvscan.screening.soap.model.VerifyEducationResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Оркестрирует: сериализация запроса -> проверка по XSD -> "вызов" заглушки -> проверка
 * ответа по XSD -> десериализация. Все исключения перехватываются и превращаются в неудачный
 * {@link EducationVerificationOutcome} — этот адаптер никогда не бросает исключения, так как
 * вызывается из precheck на виртуальном потоке, чья ошибка не должна влиять на остальной пайплайн.
 */
@Component
public class SoapEducationAdapter {

    private final EducationVerificationXmlCodec codec;
    private final EducationVerificationXsdValidator xsdValidator;
    private final EducationVerificationStub stub;

    public SoapEducationAdapter(final EducationVerificationXmlCodec codec,
                                 final EducationVerificationXsdValidator xsdValidator,
                                 final EducationVerificationStub stub) {
        this.codec = codec;
        this.xsdValidator = xsdValidator;
        this.stub = stub;
    }

    public EducationVerificationOutcome verify(final String candidateId, final String fullName, final String educationText) {
        try {
            final VerifyEducationRequest request = new VerifyEducationRequest(
                    candidateId, fullName, educationText == null ? "" : educationText);

            final String requestXml = codec.marshalRequest(request);
            final List<XmlDiagnostic> requestErrors = xsdValidator.validate(requestXml);
            if (!requestErrors.isEmpty()) {
                return EducationVerificationOutcome.invalid("REQUEST_XSD_VIOLATION", requestErrors);
            }

            final String responseXml = stub.handle(requestXml);
            final List<XmlDiagnostic> responseErrors = xsdValidator.validate(responseXml);
            if (!responseErrors.isEmpty()) {
                return EducationVerificationOutcome.invalid("RESPONSE_XSD_VIOLATION", responseErrors);
            }

            final VerifyEducationResponse response = codec.unmarshalResponse(responseXml);
            return EducationVerificationOutcome.success(response.result(), response.message());
        } catch (Exception e) {
            return EducationVerificationOutcome.invalid("ADAPTER_ERROR", List.of(new XmlDiagnostic("/", e.getMessage())));
        }
    }
}
