package io.github.nktogo.dataquality.ingestion;

import java.util.UUID;

public interface ValidationRunAccess {

  void requireValidationRun(UUID runId);
}
