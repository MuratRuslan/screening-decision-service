package kg.tunduk.test.senior.screeningdecisionservice.messaging.consumer;

import kg.tunduk.test.senior.screeningdecisionservice.generated.kafka.CvParsedEvent;
import kg.tunduk.test.senior.screeningdecisionservice.service.DecisionProcessingService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CvParsedListener {

    private final DecisionProcessingService decisionProcessingService;

    public CvParsedListener(DecisionProcessingService decisionProcessingService) {
        this.decisionProcessingService = decisionProcessingService;
    }

    /**
     * Consumes the raw payload as a String (needed so schema validation runs against the raw
     * JSON before databinding - see {@link DecisionProcessingService#parseAndValidate}), then
     * hands the typed, already-validated {@link CvParsedEvent} on to the business pipeline.
     */
    @KafkaListener(topics = "${app.kafka.topics.cv-parsed}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            MDC.put("candidateId", record.key());
            CvParsedEvent event = decisionProcessingService.parseAndValidate(record.value());
            decisionProcessingService.process(event);
        } finally {
            MDC.clear();
        }
    }
}
