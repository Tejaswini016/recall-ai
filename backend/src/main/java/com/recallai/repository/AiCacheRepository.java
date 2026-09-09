package com.recallai.repository;

import com.recallai.entity.AiCacheEntry;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiCacheRepository extends JpaRepository<AiCacheEntry, Long> {

    Optional<AiCacheEntry> findByContentHashAndOperationTypeAndModelAndPromptVersion(
            String contentHash, String operationType, String model, String promptVersion);
}
