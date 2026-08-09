package kg.tunduk.cvscan.screening;

import org.springframework.boot.SpringApplication;

public class TestScreeningDecisionServiceApplication {

    public static void main(final String[] args) {
        SpringApplication.from(ScreeningDecisionApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
