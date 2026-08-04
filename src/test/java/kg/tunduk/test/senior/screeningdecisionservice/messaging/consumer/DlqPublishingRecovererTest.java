package kg.tunduk.test.senior.screeningdecisionservice.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import kg.tunduk.test.senior.screeningdecisionservice.dto.kafka.DlqEvent;
import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.ErrorDetail;
import kg.tunduk.test.senior.screeningdecisionservice.exception.NonRetryableEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DlqPublishingRecovererTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private DlqPublishingRecoverer recoverer() {
        return new DlqPublishingRecoverer(kafkaTemplate, MAPPER, "screening.decision.dlq");
    }

    @Test
    void extractsErrorCodeAndDetailsFromWrappedNonRetryableException() throws Exception {
        NonRetryableEventException cause = new NonRetryableEventException("SCHEMA_VALIDATION_ERROR", "bad key",
                List.of(new ErrorDetail(null, "must match pattern", "/criteria/0/key")));
        ListenerExecutionFailedException wrapped = new ListenerExecutionFailedException("listener failed", cause);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("cv.parsed", 0, 42L, "candidate-1",
                "{\"key\":\"value\"}");

        recoverer().accept(record, wrapped);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("screening.decision.dlq"), eq("candidate-1"), payloadCaptor.capture());

        DlqEvent dlqEvent = MAPPER.readValue(payloadCaptor.getValue(), DlqEvent.class);
        assertThat(dlqEvent.errorCode()).isEqualTo("SCHEMA_VALIDATION_ERROR");
        assertThat(dlqEvent.errorMessage()).isEqualTo("bad key");
        assertThat(dlqEvent.details()).hasSize(1);
        assertThat(dlqEvent.details().get(0).pointer()).isEqualTo("/criteria/0/key");
        assertThat(dlqEvent.sourceTopic()).isEqualTo("cv.parsed");
        assertThat(dlqEvent.partition()).isEqualTo(0);
        assertThat(dlqEvent.offset()).isEqualTo(42L);
    }

    @Test
    void fallsBackToRawStringWhenPayloadIsNotValidJson() throws Exception {
        ListenerExecutionFailedException wrapped = new ListenerExecutionFailedException("listener failed",
                new RuntimeException("boom"));
        ConsumerRecord<String, String> record = new ConsumerRecord<>("cv.parsed", 0, 1L, "candidate-2", "not json at all");

        recoverer().accept(record, wrapped);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("screening.decision.dlq"), eq("candidate-2"), payloadCaptor.capture());

        DlqEvent dlqEvent = MAPPER.readValue(payloadCaptor.getValue(), DlqEvent.class);
        assertThat(dlqEvent.errorCode()).isEqualTo("PROCESSING_ERROR");
        assertThat(dlqEvent.originalPayload()).isEqualTo("not json at all");
    }
}
