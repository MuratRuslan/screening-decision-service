package kg.tunduk.cvscan.screening.exception;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FieldErrorToPointerMapperTest {

    @ParameterizedTest
    @CsvSource({
            "position, /position",
            "weights[0].key, /weights/0/key",
            "weights[2].nested[1].value, /weights/2/nested/1/value"
    })
    void convertsFieldPathToJsonPointer(final String fieldPath, final String expectedPointer) {
        assertThat(FieldErrorToPointerMapper.toPointer(fieldPath)).isEqualTo(expectedPointer);
    }
}
