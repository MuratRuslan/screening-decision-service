package kg.tunduk.test.senior.screeningdecisionservice.controller;

import jakarta.validation.Valid;
import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.RuleSetRequest;
import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.RuleSetResponse;
import kg.tunduk.test.senior.screeningdecisionservice.service.RuleSetService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rule-sets")
public class RuleSetController {

    private final RuleSetService ruleSetService;

    public RuleSetController(RuleSetService ruleSetService) {
        this.ruleSetService = ruleSetService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleSetResponse createRuleSet(@Valid @RequestBody RuleSetRequest request) {
        return ruleSetService.create(request);
    }

    @GetMapping("/active")
    public RuleSetResponse getActiveRuleSet(@RequestParam String position) {
        return ruleSetService.findActive(position);
    }
}
