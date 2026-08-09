package kg.tunduk.cvscan.screening.config;

import kg.tunduk.cvscan.screening.soap.EducationVerificationXmlCodec;
import kg.tunduk.cvscan.screening.soap.EducationVerificationXsdValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class XsdValidationConfig {

    @Bean
    public EducationVerificationXsdValidator educationVerificationXsdValidator() throws IOException, SAXException {
        try (final InputStream in = new ClassPathResource("contract/soap/education-verification.xsd").getInputStream()) {
            return new EducationVerificationXsdValidator(EducationVerificationXsdValidator.loadSchema(in));
        }
    }

    @Bean
    public EducationVerificationXmlCodec educationVerificationXmlCodec() {
        return new EducationVerificationXmlCodec();
    }
}
