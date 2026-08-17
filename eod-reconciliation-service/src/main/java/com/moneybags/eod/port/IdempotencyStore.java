package com.moneybags.eod.port;

import java.util.Optional;

public interface IdempotencyStore {
    record Entry(String scope, String key, String requestFingerprint, String resourceId) {}
    Optional<Entry> find(String scope, String key);
    void save(Entry entry);
}
