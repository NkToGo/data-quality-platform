package io.github.nktogo.dataquality.dataset;

import java.util.UUID;

public interface ValidationProfileAccess {

  UUID requireValidationProfileDatasetId(UUID profileId);
}
