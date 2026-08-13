package com.moneybags.productmaster.repository;

import com.moneybags.productmaster.entity.CreditCardProduct;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditCardProductRepository extends JpaRepository<CreditCardProduct, String> {
    Optional<CreditCardProduct> findByProductCode(String productCode);
    boolean existsByProductCode(String productCode);
}
