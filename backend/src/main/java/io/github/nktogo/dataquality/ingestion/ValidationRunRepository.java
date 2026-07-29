package io.github.nktogo.dataquality.ingestion;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ValidationRunRepository extends JpaRepository<ValidationRun, UUID> {

  List<ValidationRun> findAllByOrderByIdAsc();
}
