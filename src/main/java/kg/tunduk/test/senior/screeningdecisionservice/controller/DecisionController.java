package kg.tunduk.test.senior.screeningdecisionservice.controller;

import jakarta.validation.Valid;
import kg.tunduk.test.senior.screeningdecisionservice.generated.rest.model.AuditEntry;
import kg.tunduk.test.senior.screeningdecisionservice.generated.rest.model.DecisionOverrideRequest;
import kg.tunduk.test.senior.screeningdecisionservice.generated.rest.model.DecisionPage;
import kg.tunduk.test.senior.screeningdecisionservice.generated.rest.model.DecisionResponse;
import kg.tunduk.test.senior.screeningdecisionservice.model.SourceVerdict;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.Decision;
import kg.tunduk.test.senior.screeningdecisionservice.service.DecisionOverrideService;
import kg.tunduk.test.senior.screeningdecisionservice.service.DecisionQueryService;
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

    public DecisionController(DecisionQueryService decisionQueryService, DecisionOverrideService decisionOverrideService) {
        this.decisionQueryService = decisionQueryService;
        this.decisionOverrideService = decisionOverrideService;
    }

    @GetMapping
    public DecisionPage listDecisions(
            @RequestParam(required = false) String position,
            @RequestParam(required = false) Decision decision,
            @RequestParam(required = false) SourceVerdict sourceVerdict,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "decidedAt,desc") String sort) {
        return decisionQueryService.list(position, decision, sourceVerdict, minScore, search, page, size, sort);
    }

    @GetMapping("/{id}")
    public DecisionResponse getDecision(@PathVariable UUID id) {
        return decisionQueryService.get(id);
    }

    @GetMapping("/by-candidate/{candidateId}")
    public DecisionResponse getLatestDecisionByCandidate(@PathVariable String candidateId) {
        return decisionQueryService.getLatestByCandidate(candidateId);
    }

    @PatchMapping("/{id}/override")
    public DecisionResponse overrideDecision(@PathVariable UUID id,
                                              @RequestHeader("expectedVersion") int expectedVersion,
                                              @Valid @RequestBody DecisionOverrideRequest request) {
        return decisionOverrideService.override(id, expectedVersion, request);
    }

    @GetMapping("/{id}/audit")
    public List<AuditEntry> getDecisionAudit(@PathVariable UUID id) {
        return decisionQueryService.audit(id);
    }
}
