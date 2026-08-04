package kg.tunduk.test.senior.screeningdecisionservice.dto.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

public record RuleSetRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9-]+$")
        String position,

        @NotBlank
        @Pattern(regexp = "^v[0-9]+$")
        String version,

        @NotNull
        Instant activeFrom,

        @NotNull
        @Min(0)
        @Max(100)
        Integer minApproveScore,

        @NotNull
        @Min(0)
        @Max(100)
        Integer maxRejectScore,

        @NotEmpty
        @Valid
        List<CriterionWeightRequest> weights
) {
}
