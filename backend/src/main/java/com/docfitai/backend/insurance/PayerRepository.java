package com.docfitai.backend.insurance;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayerRepository extends JpaRepository<Payer, Long> {

    List<Payer> findAllByOrderByNameAsc();

    Optional<Payer> findByCode(String code);
}
