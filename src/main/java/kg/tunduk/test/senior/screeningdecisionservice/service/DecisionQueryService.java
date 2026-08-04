package kg.tunduk.test.senior.screeningdecisionservice.service;

import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.AuditEntryResponse;
import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.DecisionPage;
import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.DecisionResponse;
import kg.tunduk.test.senior.screeningdecisionservice.exception.BadRequestException;
import kg.tunduk.test.senior.screeningdecisionservice.exception.NotFoundException;
import kg.tunduk.test.senior.screeningdecisionservice.mapper.AuditMapper;
import kg.tunduk.test.senior.screeningdecisionservice.mapper.DecisionMapper;
import kg.tunduk.test.senior.screeningdecisionservice.model.ScreeningDecisionEntity;
import kg.tunduk.test.senior.screeningdecisionservice.model.SourceVerdict;
import kg.tunduk.test.senior.screeningdecisionservice.repository.DecisionAuditRepository;
import kg.tunduk.test.senior.screeningdecisionservice.repository.ScreeningDecisionRepository;
import kg.tunduk.test.senior.screeningdecisionservice.repository.ScreeningDecisionSpecifications;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.Decision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DecisionQueryService {

    /** Property names (not column names) - Spring Data translates them via the entity mapping. */
    private static final Map<String, String> SORTABLE_PROPERTIES = Map.of(
            "score", "score",
            "decidedAt", "decidedAt",
            "candidateId", "candidateId"
    );

    private final ScreeningDecisionRepository decisionRepository;
    private final DecisionAuditRepository auditRepository;

    public DecisionQueryService(ScreeningDecisionRepository decisionRepository, DecisionAuditRepository auditRepository) {
        this.decisionRepository = decisionRepository;
        this.auditRepository = auditRepository;
    }

    public DecisionPage list(String position, Decision decision, SourceVerdict sourceVerdict, Integer minScore,
                              String search, int page, int size, String sort) {
        Specification<ScreeningDecisionEntity> spec =
                ScreeningDecisionSpecifications.filter(position, decision, sourceVerdict, minScore, search);
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));

        Page<ScreeningDecisionEntity> result = decisionRepository.findAll(spec, pageable);
        List<DecisionResponse> content = result.getContent().stream().map(DecisionMapper::toResponse).toList();

        return new DecisionPage(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public DecisionResponse get(UUID id) {
        return DecisionMapper.toResponse(findEntity(id));
    }

    public DecisionResponse getLatestByCandidate(String candidateId) {
        ScreeningDecisionEntity entity = decisionRepository.findFirstByCandidateIdOrderByDecidedAtDesc(candidateId)
                .orElseThrow(() -> new NotFoundException("Решения для кандидата '" + candidateId + "' не найдены"));
        return DecisionMapper.toResponse(entity);
    }

    public List<AuditEntryResponse> audit(UUID decisionId) {
        if (!decisionRepository.existsById(decisionId)) {
            throw new NotFoundException("Решение " + decisionId + " не найдено");
        }
        return auditRepository.findByDecisionIdOrderByCreatedAtAsc(decisionId).stream()
                .map(AuditMapper::toResponse)
                .toList();
    }

    ScreeningDecisionEntity findEntity(UUID id) {
        return decisionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Решение " + id + " не найдено"));
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "decidedAt");
        }
        String[] parts = sort.split(",", 2);
        String property = SORTABLE_PROPERTIES.get(parts[0].trim());
        if (property == null) {
            throw new BadRequestException("Недопустимое поле сортировки: " + parts[0].trim());
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length > 1 && !parts[1].isBlank()) {
            try {
                direction = Sort.Direction.fromString(parts[1].trim());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Недопустимое направление сортировки: " + parts[1].trim());
            }
        }
        return Sort.by(direction, property);
    }
}
