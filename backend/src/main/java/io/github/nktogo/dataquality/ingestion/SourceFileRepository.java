package io.github.nktogo.dataquality.ingestion;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SourceFileRepository extends JpaRepository<SourceFile, UUID> {

  @Query("select sourceFile.datasetId from SourceFile sourceFile where sourceFile.id = :fileId")
  Optional<UUID> findDatasetIdById(@Param("fileId") UUID fileId);

  @Query("select sourceFile.contentBytes from SourceFile sourceFile where sourceFile.id = :fileId")
  Optional<byte[]> findContentBytesById(@Param("fileId") UUID fileId);
}
