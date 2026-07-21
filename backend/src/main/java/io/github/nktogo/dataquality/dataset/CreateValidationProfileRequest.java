package io.github.nktogo.dataquality.dataset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateValidationProfileRequest(@NotBlank @Size(max = 255) String name) {}
