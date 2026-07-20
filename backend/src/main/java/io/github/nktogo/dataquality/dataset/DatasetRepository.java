package io.github.nktogo.dataquality.dataset;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DatasetRepository extends JpaRepository<Dataset, UUID> {

  List<Dataset> findAllByOrderByCreatedAtAscIdAsc();
}
