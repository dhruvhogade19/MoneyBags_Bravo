package com.moneybags.cif.repository;

import com.moneybags.cif.entity.Cif;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CifRepository extends JpaRepository<Cif, Long> {

    boolean existsByEmail(String email);

    boolean existsByNumber(String number);

    boolean existsByPanNumber(String panNumber);

    boolean existsByAadhaarNumber(String aadhaarNumber);

    boolean existsByEmailAndCifIdNot(String email, Long cifId);

    boolean existsByNumberAndCifIdNot(String number, Long cifId);

    boolean existsByPanNumberAndCifIdNot(String panNumber, Long cifId);

    boolean existsByAadhaarNumberAndCifIdNot(String aadhaarNumber, Long cifId);
}

// By extending JpaRepository<Cif, Long>, Spring automatically provides common database operations such as:
//save()
//findById()
//findAll()
//deleteById()


// functions ending with cifIdNot means they are for updating, So that