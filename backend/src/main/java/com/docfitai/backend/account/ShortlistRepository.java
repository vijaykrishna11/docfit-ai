package com.docfitai.backend.account;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortlistRepository extends JpaRepository<Shortlist, Long> {

    List<Shortlist> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Shortlist> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}
