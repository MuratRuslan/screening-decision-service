package kg.tunduk.test.senior.screeningdecisionservice.precheck;

import kg.tunduk.test.senior.screeningdecisionservice.dto.kafka.CvParsedEvent;

/**
 * One I/O-bound pre-decision check. Implementations perform their own simulated latency and
 * must never throw - {@link PrecheckOrchestrator} defends against that too, but a check
 * returning {@link PrecheckStatus#FAILED} explicitly is preferred over relying on the
 * orchestrator's catch-all.
 */
public interface PrecheckCheck {

    String name();

    PrecheckResult run(CvParsedEvent event);
}
