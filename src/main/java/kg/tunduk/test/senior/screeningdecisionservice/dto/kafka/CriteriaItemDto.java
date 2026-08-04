package kg.tunduk.test.senior.screeningdecisionservice.dto.kafka;

/** Mirrors the {@code criteria[]} items of the {@code cv.parsed} event, pre-normalization. */
public record CriteriaItemDto(String key, String result, String comment) {
}
