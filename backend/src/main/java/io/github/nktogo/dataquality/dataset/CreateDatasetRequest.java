package io.github.nktogo.dataquality.dataset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDatasetRequest(
    @NotBlank @Size(max = 255) String name, @Size(max = 2000) String description) {}
