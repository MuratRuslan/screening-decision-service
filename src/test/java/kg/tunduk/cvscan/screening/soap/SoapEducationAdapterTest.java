package kg.tunduk.cvscan.screening.soap;

import kg.tunduk.cvscan.screening.soap.model.EducationVerificationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class SoapEducationAdapterTest {

    private static SoapEducationAdapter adapter;

    @BeforeAll
    static void setUp() throws Exception {
        EducationVerificationXmlCodec codec = new EducationVerificationXmlCodec();
        EducationVerificationXsdValidator validator;
        try (InputStream in = SoapEducationAdapterTest.class.getClassLoader()
                .getResourceAsStream("contract/soap/education-verification.xsd")) {
            validator = new EducationVerificationXsdValidator(EducationVerificationXsdValidator.loadSchema(in));
        }
        EducationVerificationStub stub = new EducationVerificationStub(codec);
        adapter = new SoapEducationAdapter(codec, validator, stub);
    }

    @Test
    void wellFormedEducationWithYearIsVerified() {
        EducationVerificationOutcome outcome = adapter.verify("senior-test", "Тест Тестов", "КГТУ им. Раззакова, ИТ, 2018");

        assertThat(outcome.valid()).isTrue();
        assertThat(outcome.result()).isEqualTo(EducationVerificationResult.VERIFIED);
    }

    @Test
    void substantiveTextWithoutYearNeedsReview() {
        EducationVerificationOutcome outcome = adapter.verify("senior-test", "Тест Тестов", "Какой-то университет без указания года");

        assertThat(outcome.valid()).isTrue();
        assertThat(outcome.result()).isEqualTo(EducationVerificationResult.NEEDS_REVIEW);
    }

    @Test
    void blankEducationIsInvalidFormat() {
        EducationVerificationOutcome outcome = adapter.verify("senior-test", "Тест Тестов", "");

        assertThat(outcome.valid()).isTrue();
        assertThat(outcome.result()).isEqualTo(EducationVerificationResult.INVALID_FORMAT);
    }

    @Test
    void nullEducationIsInvalidFormatRatherThanThrowing() {
        EducationVerificationOutcome outcome = adapter.verify("senior-test", "Тест Тестов", null);

        assertThat(outcome.valid()).isTrue();
        assertThat(outcome.result()).isEqualTo(EducationVerificationResult.INVALID_FORMAT);
    }
}
