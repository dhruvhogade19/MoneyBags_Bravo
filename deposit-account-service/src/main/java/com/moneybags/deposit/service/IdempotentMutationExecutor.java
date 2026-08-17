package com.moneybags.deposit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneybags.deposit.config.DepositAccountProperties;
import com.moneybags.deposit.entity.IdempotencyRecord;
import com.moneybags.deposit.exception.ApiException;
import com.moneybags.deposit.repository.IdempotencyRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotentMutationExecutor {
    private final IdempotencyRecordRepository repository;
    private final DepositAccountProperties properties;
    private final ObjectMapper objectMapper;

    public IdempotentMutationExecutor(IdempotencyRecordRepository repository,
                                      DepositAccountProperties properties,
                                      ObjectMapper objectMapper) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public <T> T execute(String scope, String key, Object requestIdentity, Class<T> responseType,
                         Supplier<T> mutation) {
        return execute(scope, key, requestIdentity, objectMapper.getTypeFactory().constructType(responseType), mutation);
    }

    @Transactional
    public <T> T execute(String scope, String key, Object requestIdentity, TypeReference<T> responseType,
                         Supplier<T> mutation) {
        return execute(scope, key, requestIdentity,
                objectMapper.getTypeFactory().constructType(responseType), mutation);
    }

    @Transactional
    public void executeVoid(String scope, String key, Object requestIdentity, Runnable mutation) {
        execute(scope, key, requestIdentity, Void.class, () -> {
            mutation.run();
            return null;
        });
    }

    private <T> T execute(String scope, String key, Object requestIdentity, JavaType responseType,
                          Supplier<T> mutation) {
        requireKey(key);
        String keyHash = Hashing.sha256(key);
        String requestHash = Hashing.sha256(write(requestIdentity));
        var prior = repository.findByScopeAndKeyHash(scope, keyHash);
        if (prior.isPresent()) {
            IdempotencyRecord record = prior.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "The idempotency key was already used with a different request");
            }
            if (!"COMPLETED".equals(record.getProcessingStatus())) {
                throw new ApiException(HttpStatus.CONFLICT, "REQUEST_ALREADY_PROCESSING",
                        "A request with this idempotency key is already processing");
            }
            return read(record.getResponseBody(), responseType);
        }

        IdempotencyRecord record = new IdempotencyRecord(UUID.randomUUID().toString(), scope, keyHash,
                requestHash, OffsetDateTime.now().plusHours(properties.idempotencyTtlHours()));
        repository.save(record);
        T response = mutation.get();
        record.setProcessingStatus("COMPLETED");
        record.setHttpStatus(200);
        record.setResponseBody(write(response));
        repository.save(record);
        return response;
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key is required and must be at most 128 characters");
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCY_SERIALIZATION_FAILED",
                    "Unable to persist the idempotent operation result");
        }
    }

    private <T> T read(String value, JavaType type) {
        if (type.hasRawClass(Void.class)) return null;
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCY_REPLAY_FAILED",
                    "Unable to replay the idempotent operation result");
        }
    }
}
