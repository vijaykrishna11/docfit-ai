package com.docfitai.backend.navigator;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserReminderRepository extends JpaRepository<UserReminder, Long> {

    Optional<UserReminder> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}
