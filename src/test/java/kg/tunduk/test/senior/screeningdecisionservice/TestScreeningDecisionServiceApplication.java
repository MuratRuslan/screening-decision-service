package kg.tunduk.test.senior.screeningdecisionservice;

import org.springframework.boot.SpringApplication;

public class TestScreeningDecisionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(ScreeningDecisionApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
