package kg.tunduk.test.senior.screeningdecisionservice.service;

import kg.tunduk.test.senior.screeningdecisionservice.exception.DuplicateRuleSetException;
import kg.tunduk.test.senior.screeningdecisionservice.exception.NotFoundException;
import kg.tunduk.test.senior.screeningdecisionservice.exception.RequestValidationException;
import kg.tunduk.test.senior.screeningdecisionservice.generated.rest.model.CriterionWeight;
import kg.tunduk.test.senior.screeningdecisionservice.generated.rest.model.ErrorResponseDetailsInner;
import kg.tunduk.test.senior.screeningdecisionservice.generated.rest.model.RuleSetRequest;
import kg.tunduk.test.senior.screeningdecisionservice.generated.rest.model.RuleSetResponse;
import kg.tunduk.test.senior.screeningdecisionservice.mapper.RuleSetMapper;
import kg.tunduk.test.senior.screeningdecisionservice.model.RuleSetEntity;
import kg.tunduk.test.senior.screeningdecisionservice.repository.RuleSetRepository;
import kg.tunduk.test.senior.screeningdecisionservice.semantic.CriteriaCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RuleSetService {

    private final RuleSetRepository ruleSetRepository;
    private final CriteriaCatalog criteriaCatalog;

    public RuleSetService(RuleSetRepository ruleSetRepository, CriteriaCatalog criteriaCatalog) {
        this.ruleSetRepository = ruleSetRepository;
        this.criteriaCatalog = criteriaCatalog;
    }

    public RuleSetResponse findActive(String position) {
        RuleSetEntity entity = ruleSetRepository
                .findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(position, Instant.now())
                .orElseThrow(() -> new NotFoundException("Активный rule-set для позиции '" + position + "' не найден"));
        return RuleSetMapper.toResponse(entity);
    }

    @Transactional
    public RuleSetResponse create(RuleSetRequest request) {
        if (ruleSetRepository.findByPositionAndVersion(request.getPosition(), request.getVersion()).isPresent()) {
            throw new DuplicateRuleSetException(
                    "Rule set " + request.getPosition() + "/" + request.getVersion() + " уже существует");
        }
        validateWeightsAgainstCatalog(request.getWeights());

        List<kg.tunduk.test.senior.screeningdecisionservice.scoring.CriterionWeight> weights = request.getWeights().stream()
                .map(w -> new kg.tunduk.test.senior.screeningdecisionservice.scoring.CriterionWeight(w.getKey(), w.getWeight()))
                .toList();

        RuleSetEntity entity = new RuleSetEntity(UUID.randomUUID(), request.getPosition(), request.getVersion(),
                request.getActiveFrom().toInstant(), request.getMinApproveScore(), request.getMaxRejectScore(),
                weights, Instant.now());

        ruleSetRepository.save(entity);
        return RuleSetMapper.toResponse(entity);
    }

    /** Rule-sets may only reference canonical ids from the semantic catalog, never raw aliases. */
    private void validateWeightsAgainstCatalog(List<CriterionWeight> weights) {
        List<ErrorResponseDetailsInner> unknown = new ArrayList<>();
        for (int i = 0; i < weights.size(); i++) {
            String key = weights.get(i).getKey();
            if (!criteriaCatalog.isCanonical(key)) {
                unknown.add(new ErrorResponseDetailsInner()
                        .field("weights[" + i + "].key")
                        .message("Неизвестный канонический критерий семантического каталога: " + key)
                        .pointer("/weights/" + i + "/key"));
            }
        }
        if (!unknown.isEmpty()) {
            throw new RequestValidationException("Ошибка валидации входных данных", unknown);
        }
    }
}
