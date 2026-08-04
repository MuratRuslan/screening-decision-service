package kg.tunduk.test.senior.screeningdecisionservice.dto.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CriterionWeightRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9_:-]+$")
        String key,

        @Min(1)
        @Max(100)
        int weight
) {
}
