package com.moneybags.accounting.repository;

import com.moneybags.accounting.entity.PostingRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostingRequestRepository extends JpaRepository<PostingRequest, String> {
    Optional<PostingRequest> findByExternalReference(String externalReference);
    Optional<PostingRequest> findByIdempotencyKeyHash(String idempotencyKeyHash);
}
