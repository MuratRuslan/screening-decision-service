package kg.tunduk.test.senior.screeningdecisionservice.messaging.consumer;

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

    @KafkaListener(topics = "${app.kafka.topics.cv-parsed}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            MDC.put("candidateId", record.key());
            decisionProcessingService.process(record.value());
        } finally {
            MDC.clear();
        }
    }
}
