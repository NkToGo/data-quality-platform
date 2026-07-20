package io.github.nktogo.dataquality.dataset;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DatasetService {

  private final DatasetRepository datasetRepository;

  DatasetService(DatasetRepository datasetRepository) {
    this.datasetRepository = datasetRepository;
  }

  @Transactional
  DatasetResponse create(CreateDatasetRequest request) {
    Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    Dataset dataset = new Dataset(request.name(), request.description(), createdAt);

    return toResponse(datasetRepository.save(dataset));
  }

  @Transactional(readOnly = true)
  List<DatasetResponse> getAll() {
    return datasetRepository.findAllByOrderByCreatedAtAscIdAsc().stream()
        .map(DatasetService::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  DatasetResponse getById(UUID datasetId) {
    return datasetRepository
        .findById(datasetId)
        .map(DatasetService::toResponse)
        .orElseThrow(() -> new DatasetNotFoundException(datasetId));
  }

  private static DatasetResponse toResponse(Dataset dataset) {
    return new DatasetResponse(
        dataset.getId(), dataset.getName(), dataset.getDescription(), dataset.getCreatedAt());
  }
}
