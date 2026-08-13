package com.moneybags.productmaster.repository;

import com.moneybags.productmaster.entity.DepositProduct;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositProductRepository extends JpaRepository<DepositProduct, String> {
    Optional<DepositProduct> findByProductCode(String productCode);
    boolean existsByProductCode(String productCode);
}
