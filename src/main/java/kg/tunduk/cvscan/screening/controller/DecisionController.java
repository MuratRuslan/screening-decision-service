package kg.tunduk.cvscan.screening.controller;

import jakarta.validation.Valid;
import kg.tunduk.cvscan.screening.generated.rest.model.AuditEntry;
import kg.tunduk.cvscan.screening.generated.rest.model.DecisionOverrideRequest;
import kg.tunduk.cvscan.screening.generated.rest.model.DecisionPage;
import kg.tunduk.cvscan.screening.generated.rest.model.DecisionResponse;
import kg.tunduk.cvscan.screening.model.SourceVerdict;
import kg.tunduk.cvscan.screening.scoring.Decision;
import kg.tunduk.cvscan.screening.service.DecisionOverrideService;
import kg.tunduk.cvscan.screening.service.DecisionQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/decisions")
public class DecisionController {

    private final DecisionQueryService decisionQueryService;
    private final DecisionOverrideService decisionOverrideService;

    public DecisionController(final DecisionQueryService decisionQueryService, final DecisionOverrideService decisionOverrideService) {
        this.decisionQueryService = decisionQueryService;
        this.decisionOverrideService = decisionOverrideService;
    }

    @GetMapping
    public DecisionPage listDecisions(
            @RequestParam(required = false) final String position,
            @RequestParam(required = false) final Decision decision,
            @RequestParam(required = false) final SourceVerdict sourceVerdict,
            @RequestParam(required = false) final Integer minScore,
            @RequestParam(required = false) final String search,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size,
            @RequestParam(defaultValue = "decidedAt,desc") final String sort) {
        return decisionQueryService.list(position, decision, sourceVerdict, minScore, search, page, size, sort);
    }

    @GetMapping("/{id}")
    public DecisionResponse getDecision(@PathVariable final UUID id) {
        return decisionQueryService.get(id);
    }

    @GetMapping("/by-candidate/{candidateId}")
    public DecisionResponse getLatestDecisionByCandidate(@PathVariable final String candidateId) {
        return decisionQueryService.getLatestByCandidate(candidateId);
    }

    @PatchMapping("/{id}/override")
    public DecisionResponse overrideDecision(@PathVariable final UUID id,
                                              @RequestHeader("expectedVersion") final int expectedVersion,
                                              @Valid @RequestBody final DecisionOverrideRequest request) {
        return decisionOverrideService.override(id, expectedVersion, request);
    }

    @GetMapping("/{id}/audit")
    public List<AuditEntry> getDecisionAudit(@PathVariable final UUID id) {
        return decisionQueryService.audit(id);
    }
}
