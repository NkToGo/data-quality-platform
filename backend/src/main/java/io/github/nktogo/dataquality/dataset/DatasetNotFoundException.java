package io.github.nktogo.dataquality.dataset;

import java.util.UUID;

final class DatasetNotFoundException extends RuntimeException {

  DatasetNotFoundException(UUID datasetId) {
    super("Dataset '" + datasetId + "' was not found.");
  }
}
