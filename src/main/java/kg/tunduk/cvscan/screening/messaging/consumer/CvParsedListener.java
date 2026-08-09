package kg.tunduk.cvscan.screening.messaging.consumer;

import kg.tunduk.cvscan.screening.generated.kafka.CvParsedEvent;
import kg.tunduk.cvscan.screening.service.DecisionProcessingService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CvParsedListener {

    private final DecisionProcessingService decisionProcessingService;

    public CvParsedListener(final DecisionProcessingService decisionProcessingService) {
        this.decisionProcessingService = decisionProcessingService;
    }

    /**
     * Принимает сырой payload как String (нужно, чтобы валидация схемы шла по исходному
     * JSON до databinding - см. {@link DecisionProcessingService#parseAndValidate}), затем
     * передаёт типизированный, уже провалидированный {@link CvParsedEvent} в бизнес-пайплайн.
     */
    @KafkaListener(topics = "${app.kafka.topics.cv-parsed}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(final ConsumerRecord<String, String> record) {
        try {
            MDC.put("candidateId", record.key());
            final CvParsedEvent event = decisionProcessingService.parseAndValidate(record.value());
            decisionProcessingService.process(event);
        } finally {
            MDC.clear();
        }
    }
}
