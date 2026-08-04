package kg.tunduk.test.senior.screeningdecisionservice.dto.rest;

import java.util.List;

public record DecisionPage(
        List<DecisionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
