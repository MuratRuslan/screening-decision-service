package kg.tunduk.test.senior.screeningdecisionservice.controller;

import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.AuditEntryResponse;
import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.DecisionPage;
import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.DecisionResponse;
import kg.tunduk.test.senior.screeningdecisionservice.model.SourceVerdict;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.Decision;
import kg.tunduk.test.senior.screeningdecisionservice.service.DecisionQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/decisions")
public class DecisionController {

    private final DecisionQueryService decisionQueryService;

    public DecisionController(DecisionQueryService decisionQueryService) {
        this.decisionQueryService = decisionQueryService;
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

    @GetMapping("/{id}/audit")
    public List<AuditEntryResponse> getDecisionAudit(@PathVariable UUID id) {
        return decisionQueryService.audit(id);
    }
}
