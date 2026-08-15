package com.moneybags.kycservice.repository;

import com.moneybags.kycservice.entity.Kyc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KycRepository
        extends JpaRepository<Kyc, Long> {

    List<Kyc> findAllByCifIdOrderByCreatedAtDesc(
            Long cifId
    );

}