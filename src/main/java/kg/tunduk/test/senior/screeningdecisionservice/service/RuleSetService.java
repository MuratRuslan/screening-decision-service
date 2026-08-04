package kg.tunduk.test.senior.screeningdecisionservice.service;

import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.RuleSetResponse;
import kg.tunduk.test.senior.screeningdecisionservice.exception.NotFoundException;
import kg.tunduk.test.senior.screeningdecisionservice.mapper.RuleSetMapper;
import kg.tunduk.test.senior.screeningdecisionservice.model.RuleSetEntity;
import kg.tunduk.test.senior.screeningdecisionservice.repository.RuleSetRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class RuleSetService {

    private final RuleSetRepository ruleSetRepository;

    public RuleSetService(RuleSetRepository ruleSetRepository) {
        this.ruleSetRepository = ruleSetRepository;
    }

    public RuleSetResponse findActive(String position) {
        RuleSetEntity entity = ruleSetRepository
                .findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(position, Instant.now())
                .orElseThrow(() -> new NotFoundException("Активный rule-set для позиции '" + position + "' не найден"));
        return RuleSetMapper.toResponse(entity);
    }
}
