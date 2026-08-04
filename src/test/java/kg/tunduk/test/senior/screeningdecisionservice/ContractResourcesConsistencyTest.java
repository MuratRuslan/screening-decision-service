package kg.tunduk.test.senior.screeningdecisionservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * java-senior/ is the read-only, authoritative contract reference used for grading;
 * the running application instead loads its own classpath copies so the built jar is
 * self-contained. This test fails the build the moment those copies drift apart.
 */
class ContractResourcesConsistencyTest {

    @ParameterizedTest
    @CsvSource({
            "java-senior/contract/json-schema/cv-parsed.schema.json, contract/json-schema/cv-parsed.schema.json",
            "java-senior/contract/soap/education-verification.xsd, contract/soap/education-verification.xsd",
            "java-senior/semantic/criteria-catalog.json, semantic/criteria-catalog.json"
    })
    void classpathCopyMatchesReferenceContract(String referencePath, String classpathResource) throws IOException {
        byte[] reference = Files.readAllBytes(Path.of(referencePath));
        byte[] classpathCopy;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpathResource)) {
            assertThat(in).as("classpath resource %s must exist", classpathResource).isNotNull();
            classpathCopy = in.readAllBytes();
        }
        assertThat(new String(classpathCopy)).isEqualToNormalizingNewlines(new String(reference));
    }

    @Test
    void referenceContractDirectoryExists() {
        assertThat(Path.of("java-senior", "TASK.md")).exists();
    }
}
