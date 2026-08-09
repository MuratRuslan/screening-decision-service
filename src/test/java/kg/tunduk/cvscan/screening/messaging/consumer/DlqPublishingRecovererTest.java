package kg.tunduk.cvscan.screening.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import kg.tunduk.cvscan.screening.dto.kafka.DlqEvent;
import kg.tunduk.cvscan.screening.generated.rest.model.ErrorResponseDetailsInner;
import kg.tunduk.cvscan.screening.exception.NonRetryableEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DlqPublishingRecovererTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new JsonNullableModule());

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private DlqPublishingRecoverer recoverer() {
        return new DlqPublishingRecoverer(kafkaTemplate, MAPPER, "screening.decision.dlq");
    }

    @Test
    void extractsErrorCodeAndDetailsFromWrappedNonRetryableException() throws Exception {
        NonRetryableEventException cause = new NonRetryableEventException("SCHEMA_VALIDATION_ERROR", "bad key",
                List.of(new ErrorResponseDetailsInner().message("must match pattern").pointer("/criteria/0/key")));
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
        assertThat(dlqEvent.details().get(0).getPointer().get()).isEqualTo("/criteria/0/key");
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
