package kg.tunduk.cvscan.screening;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ScreeningDecisionApplicationTests {

    @Test
    void contextLoads() {
    }

}
