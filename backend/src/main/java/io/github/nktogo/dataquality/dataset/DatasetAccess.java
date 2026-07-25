package io.github.nktogo.dataquality.dataset;

import java.util.UUID;

public interface DatasetAccess {

  void requireDataset(UUID datasetId);
}
