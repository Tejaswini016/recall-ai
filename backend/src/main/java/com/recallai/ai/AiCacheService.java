package com.recallai.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recallai.entity.AiCacheEntry;
import com.recallai.repository.AiCacheRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Content-hash cache of validated responses. The key is
 * {@code (sha256(normalized material + params), operation, model, prompt version)}: identical
 * material from any user reuses one model call, while a model or prompt change transparently
 * starts a fresh namespace. Values are the validated domain objects, never raw model text.
 */
@Service
public class AiCacheService {

    private static final Logger log = LoggerFactory.getLogger(AiCacheService.class);

    private final AiCacheRepository repository;
    private final ObjectMapper objectMapper;

    public AiCacheService(AiCacheRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public record CacheKey(String contentHash, AiOperation operation, String model, String promptVersion) {

        public static CacheKey of(String material, String parameters, AiOperation operation, String model,
                                  String promptVersion) {
            return new CacheKey(ContentHasher.hash(material, parameters), operation, model, promptVersion);
        }
    }

    @Transactional(readOnly = true)
    public <T> Optional<List<T>> lookup(CacheKey key, TypeReference<List<T>> type) {
        Optional<AiCacheEntry> entry = repository.findByContentHashAndOperationTypeAndModelAndPromptVersion(
                key.contentHash(), key.operation().name(), key.model(), key.promptVersion());
        if (entry.isEmpty()) {
            log.info("AI cache miss for {} ({}/{})", key.operation(), key.model(), key.promptVersion());
            return Optional.empty();
        }
        try {
            List<T> items = objectMapper.readValue(entry.get().getResponseJson(), type);
            log.info("AI cache hit for {} ({}/{})", key.operation(), key.model(), key.promptVersion());
            return Optional.of(items);
        } catch (JsonProcessingException e) {
            // A corrupt row must not break generation; treat it as a miss and let it be overwritten.
            log.warn("AI cache entry {} could not be deserialized; ignoring", entry.get().getId());
            return Optional.empty();
        }
    }

    /** Stores validated items. A concurrent insert of the same key is harmless and ignored. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> void store(CacheKey key, List<T> items) {
        try {
            String json = objectMapper.writeValueAsString(items);
            repository.save(new AiCacheEntry(key.contentHash(), key.operation().name(), key.model(),
                    key.promptVersion(), json));
            repository.flush();
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize AI result for caching: {}", e.getOriginalMessage());
        } catch (DataIntegrityViolationException e) {
            log.info("AI cache entry for {} already exists; keeping the existing row", key.operation());
        }
    }
}
