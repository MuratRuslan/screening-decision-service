package kg.tunduk.test.senior.screeningdecisionservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorConfig {

    /**
     * Dedicated to prechecks only (not reused as a general {@code @Async} pool) so its
     * purpose and load are easy to reason about and test in isolation. Spring infers
     * {@code close()} as the destroy method for {@code @Bean}-declared {@link
     * AutoCloseable}s, so this shuts down cleanly on context close - {@code
     * ExecutorService.close()} (Java 19+) shuts down and awaits termination.
     */
    @Bean(name = "precheckExecutor")
    public ExecutorService precheckExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
