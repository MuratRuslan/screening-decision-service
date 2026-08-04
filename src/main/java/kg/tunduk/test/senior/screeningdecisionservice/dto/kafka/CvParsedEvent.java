package kg.tunduk.test.senior.screeningdecisionservice.dto.kafka;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Mirrors contract/json-schema/cv-parsed.schema.json, databound only after schema validation passes. */
public record CvParsedEvent(
        UUID eventId,
        String candidateId,
        Instant parsedAt,
        String name,
        String position,
        String posLabel,
        String email,
        String phone,
        String city,
        String telegram,
        String totalExp,
        String stack,
        String education,
        String verdict,
        String summary,
        List<CriteriaItemDto> criteria,
        List<ExperienceItemDto> experience,
        List<String> questions
) {
}
