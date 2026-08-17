package com.docfitai.backend.navigator;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSavedPlanRepository extends JpaRepository<UserSavedPlan, Long> {

    Optional<UserSavedPlan> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
