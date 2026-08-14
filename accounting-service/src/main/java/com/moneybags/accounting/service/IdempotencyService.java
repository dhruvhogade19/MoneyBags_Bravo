package com.moneybags.accounting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneybags.accounting.entity.IdempotencyRecord;
import com.moneybags.accounting.exception.ApiException;
import com.moneybags.accounting.repository.IdempotencyRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotencyService {
    private final IdempotencyRecordRepository records;
    private final Hashing hashing;
    private final ObjectMapper mapper;

    public IdempotencyService(IdempotencyRecordRepository records, Hashing hashing, ObjectMapper mapper) {
        this.records = records; this.hashing = hashing; this.mapper = mapper;
    }

    @Transactional
    public synchronized <T> T execute(String scope, String key, Object request, Class<T> responseType,
                                      Supplier<T> operation) {
        String keyHash = hashing.sha256(key);
        String requestHash = hashing.requestHash(request);
        return records.findByScopeAndKeyHash(scope, keyHash).map(existing -> {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "The idempotency key was already used with different request content");
            }
            try { return mapper.readValue(existing.getResponseBody(), responseType); }
            catch (JsonProcessingException ex) { throw new IllegalStateException("Stored idempotent response is invalid", ex); }
        }).orElseGet(() -> {
            T response = operation.get();
            try {
                records.save(new IdempotencyRecord(UUID.randomUUID().toString(), scope, keyHash, requestHash,
                        null, 200, mapper.writeValueAsString(response)));
                return response;
            } catch (JsonProcessingException ex) { throw new IllegalStateException("Response could not be stored", ex); }
        });
    }
}
