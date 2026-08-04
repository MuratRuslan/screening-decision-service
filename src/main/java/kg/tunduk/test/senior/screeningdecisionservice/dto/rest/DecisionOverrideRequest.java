package kg.tunduk.test.senior.screeningdecisionservice.dto.rest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.Decision;

public record DecisionOverrideRequest(
        @NotNull
        Decision decision,

        @NotNull
        @Size(min = 10, max = 1000)
        String reason
) {
}
