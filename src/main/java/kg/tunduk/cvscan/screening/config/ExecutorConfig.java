package kg.tunduk.cvscan.screening.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorConfig {

    /**
     * Используется только для прекheck'ов (не переиспользуется как общий пул {@code @Async}),
     * чтобы назначение и нагрузку было легко понимать и тестировать отдельно. Spring сам
     * определяет {@code close()} как destroy-метод для {@code @Bean}, реализующих {@link
     * AutoCloseable}, поэтому пул корректно останавливается при закрытии контекста - {@code
     * ExecutorService.close()} (Java 19+) останавливает и дожидается завершения.
     */
    @Bean(name = "precheckExecutor")
    public ExecutorService precheckExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
