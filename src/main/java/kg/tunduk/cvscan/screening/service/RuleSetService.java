package kg.tunduk.cvscan.screening.service;

import kg.tunduk.cvscan.screening.exception.DuplicateRuleSetException;
import kg.tunduk.cvscan.screening.exception.NotFoundException;
import kg.tunduk.cvscan.screening.exception.RequestValidationException;
import kg.tunduk.cvscan.screening.generated.rest.model.CriterionWeight;
import kg.tunduk.cvscan.screening.generated.rest.model.ErrorResponseDetailsInner;
import kg.tunduk.cvscan.screening.generated.rest.model.RuleSetRequest;
import kg.tunduk.cvscan.screening.generated.rest.model.RuleSetResponse;
import kg.tunduk.cvscan.screening.mapper.RuleSetMapper;
import kg.tunduk.cvscan.screening.model.RuleSetEntity;
import kg.tunduk.cvscan.screening.repository.RuleSetRepository;
import kg.tunduk.cvscan.screening.semantic.CriteriaCatalog;
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

    public RuleSetService(final RuleSetRepository ruleSetRepository, final CriteriaCatalog criteriaCatalog) {
        this.ruleSetRepository = ruleSetRepository;
        this.criteriaCatalog = criteriaCatalog;
    }

    public RuleSetResponse findActive(final String position) {
        final RuleSetEntity entity = ruleSetRepository
                .findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(position, Instant.now())
                .orElseThrow(() -> new NotFoundException("Активный rule-set для позиции '" + position + "' не найден"));
        return RuleSetMapper.toResponse(entity);
    }

    @Transactional
    public RuleSetResponse create(final RuleSetRequest request) {
        if (ruleSetRepository.findByPositionAndVersion(request.getPosition(), request.getVersion()).isPresent()) {
            throw new DuplicateRuleSetException(
                    "Rule set " + request.getPosition() + "/" + request.getVersion() + " уже существует");
        }
        validateWeightsAgainstCatalog(request.getWeights());

        final List<kg.tunduk.cvscan.screening.scoring.CriterionWeight> weights = request.getWeights().stream()
                .map(w -> new kg.tunduk.cvscan.screening.scoring.CriterionWeight(w.getKey(), w.getWeight()))
                .toList();

        final RuleSetEntity entity = new RuleSetEntity(UUID.randomUUID(), request.getPosition(), request.getVersion(),
                request.getActiveFrom().toInstant(), request.getMinApproveScore(), request.getMaxRejectScore(),
                weights, Instant.now());

        ruleSetRepository.save(entity);
        return RuleSetMapper.toResponse(entity);
    }

    /** Rule-set может ссылаться только на канонические id семантического каталога, не на алиасы. */
    private void validateWeightsAgainstCatalog(final List<CriterionWeight> weights) {
        final List<ErrorResponseDetailsInner> unknown = new ArrayList<>();
        for (int i = 0; i < weights.size(); i++) {
            final String key = weights.get(i).getKey();
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
