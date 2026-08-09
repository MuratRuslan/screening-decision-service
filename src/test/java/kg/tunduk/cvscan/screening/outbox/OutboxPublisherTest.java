package kg.tunduk.cvscan.screening.outbox;

import kg.tunduk.cvscan.screening.messaging.producer.DecisionEventProducer;
import kg.tunduk.cvscan.screening.messaging.producer.KafkaSendException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private DecisionEventProducer decisionEventProducer;

    private OutboxPublisher publisher() {
        return new OutboxPublisher(outboxRepository, decisionEventProducer, 50, 5000L);
    }

    @Test
    void emptyBatchNeverCallsProducer() {
        when(outboxRepository.claimBatch(50)).thenReturn(List.of());

        publisher().publishBatch();

        verify(decisionEventProducer, never()).send(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void successfulSendMarksEventSent() {
        OutboxEvent event = OutboxEvent.newEvent(UUID.randomUUID(), "SCREENING_DECISION",
                "screening.decision.created", "{\"foo\":\"bar\"}");
        when(outboxRepository.claimBatch(50)).thenReturn(List.of(event));
        doNothing().when(decisionEventProducer).send(anyString(), anyString(), anyString(), anyLong());

        publisher().publishBatch();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(event.getSentAt()).isNotNull();
        assertThat(event.getRetryCount()).isZero();
    }

    @Test
    void failedSendIncrementsRetryCountAndStaysNew() {
        OutboxEvent event = OutboxEvent.newEvent(UUID.randomUUID(), "SCREENING_DECISION",
                "screening.decision.created", "{\"foo\":\"bar\"}");
        when(outboxRepository.claimBatch(50)).thenReturn(List.of(event));
        doThrow(new KafkaSendException("broker unavailable", new RuntimeException()))
                .when(decisionEventProducer).send(anyString(), anyString(), anyString(), anyLong());

        publisher().publishBatch();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.NEW);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("broker unavailable");
        assertThat(event.getSentAt()).isNull();
    }

    @Test
    void oneFailureDoesNotStopTheRestOfTheBatch() {
        OutboxEvent failing = OutboxEvent.newEvent(UUID.randomUUID(), "SCREENING_DECISION",
                "screening.decision.created", "{}");
        OutboxEvent succeeding = OutboxEvent.newEvent(UUID.randomUUID(), "SCREENING_DECISION",
                "screening.decision.created", "{}");
        when(outboxRepository.claimBatch(50)).thenReturn(List.of(failing, succeeding));
        doThrow(new KafkaSendException("timeout", new RuntimeException()))
                .doNothing()
                .when(decisionEventProducer).send(anyString(), anyString(), anyString(), anyLong());

        publisher().publishBatch();

        assertThat(failing.getStatus()).isEqualTo(OutboxStatus.NEW);
        assertThat(succeeding.getStatus()).isEqualTo(OutboxStatus.SENT);
        verify(decisionEventProducer, times(2)).send(anyString(), anyString(), anyString(), anyLong());
    }
}
