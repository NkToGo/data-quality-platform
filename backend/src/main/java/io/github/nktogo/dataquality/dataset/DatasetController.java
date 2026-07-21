package io.github.nktogo.dataquality.dataset;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/datasets")
class DatasetController {

  private final DatasetService datasetService;

  DatasetController(DatasetService datasetService) {
    this.datasetService = datasetService;
  }

  @PostMapping
  ResponseEntity<DatasetResponse> create(@Valid @RequestBody CreateDatasetRequest request) {
    DatasetResponse response = datasetService.create(request);
    URI location = URI.create("/api/datasets/" + response.id());

    return ResponseEntity.created(location).body(response);
  }

  @GetMapping
  List<DatasetResponse> getAll() {
    return datasetService.getAll();
  }

  @GetMapping("/{datasetId}")
  DatasetResponse getById(@PathVariable UUID datasetId) {
    return datasetService.getById(datasetId);
  }
}
