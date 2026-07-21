package io.github.nktogo.dataquality.dataset;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ValidationProfileRepository extends JpaRepository<ValidationProfile, UUID> {

  List<ValidationProfile> findAllByDatasetOrderByCreatedAtAscIdAsc(Dataset dataset);
}
