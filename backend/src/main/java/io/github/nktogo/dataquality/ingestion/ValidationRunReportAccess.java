package io.github.nktogo.dataquality.ingestion;

import java.util.UUID;

public interface ValidationRunReportAccess {

  ValidationRunResponse getValidationRunForReport(UUID runId);
}
