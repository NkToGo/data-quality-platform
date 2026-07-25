package io.github.nktogo.dataquality.ingestion;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SourceFileRepository extends JpaRepository<SourceFile, UUID> {}
