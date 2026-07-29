package io.github.nktogo.dataquality.dataset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ValidationProfileRepository extends JpaRepository<ValidationProfile, UUID> {

  List<ValidationProfile> findAllByDatasetOrderByCreatedAtAscIdAsc(Dataset dataset);

  @Query(
      """
      select validationProfile.dataset.id
      from ValidationProfile validationProfile
      where validationProfile.id = :profileId
      """)
  Optional<UUID> findDatasetIdById(@Param("profileId") UUID profileId);
}
