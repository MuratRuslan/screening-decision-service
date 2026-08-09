package kg.tunduk.cvscan.screening.soap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EducationVerificationXsdValidatorTest {

    private static final String NS = "http://cv-scan.local/education-verification";

    private static EducationVerificationXsdValidator validator;

    @BeforeAll
    static void loadValidator() throws Exception {
        try (final InputStream in = EducationVerificationXsdValidatorTest.class.getClassLoader()
                .getResourceAsStream("contract/soap/education-verification.xsd")) {
            validator = new EducationVerificationXsdValidator(EducationVerificationXsdValidator.loadSchema(in));
        }
    }

    @Test
    void validRequestXmlProducesNoDiagnostics() {
        final String xml = """
                <VerifyEducationRequest xmlns="%s">
                    <candidateId>senior-test</candidateId>
                    <fullName>Тест Тестов</fullName>
                    <educationText>КГТУ, 2018</educationText>
                </VerifyEducationRequest>
                """.formatted(NS);

        assertThat(validator.validate(xml)).isEmpty();
    }

    @Test
    void validResponseXmlWithoutOptionalMessageProducesNoDiagnostics() {
        final String xml = """
                <VerifyEducationResponse xmlns="%s">
                    <candidateId>senior-test</candidateId>
                    <result>VERIFIED</result>
                </VerifyEducationResponse>
                """.formatted(NS);

        assertThat(validator.validate(xml)).isEmpty();
    }

    @Test
    void missingRequiredElementIsReported() {
        final String xml = """
                <VerifyEducationRequest xmlns="%s">
                    <candidateId>senior-test</candidateId>
                </VerifyEducationRequest>
                """.formatted(NS);

        final List<XmlDiagnostic> diagnostics = validator.validate(xml);

        assertThat(diagnostics).isNotEmpty();
    }

    @Test
    void invalidEnumValueIsReportedWithElementPath() {
        final String xml = """
                <VerifyEducationResponse xmlns="%s">
                    <candidateId>senior-test</candidateId>
                    <result>MAYBE</result>
                </VerifyEducationResponse>
                """.formatted(NS);

        final List<XmlDiagnostic> diagnostics = validator.validate(xml);

        assertThat(diagnostics).isNotEmpty();
        assertThat(diagnostics).anySatisfy(d -> assertThat(d.path()).contains("result"));
    }

    @Test
    void malformedXmlIsReportedRatherThanThrowing() {
        final List<XmlDiagnostic> diagnostics = validator.validate("<not-even-xml");

        assertThat(diagnostics).isNotEmpty();
    }
}
