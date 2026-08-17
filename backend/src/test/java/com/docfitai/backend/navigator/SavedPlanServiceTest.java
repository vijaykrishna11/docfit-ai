package com.docfitai.backend.navigator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.docfitai.backend.account.AppUser;
import com.docfitai.backend.account.AppUserRepository;
import com.docfitai.backend.navigator.dto.SavedPlanDto;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class SavedPlanServiceTest extends PostgresIntegrationSupport {

    @Autowired
    private SavedPlanService savedPlanService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void noSavedPlanByDefault() {
        Long userId = insertUser("plan-none@example.com");
        assertThat(savedPlanService.get(userId)).isNull();
    }

    @Test
    void savingAndReplacingKeepsExactlyOneRowPerUser() {
        Long userId = insertUser("plan-replace@example.com");
        Long planA = insertInsurancePlan(jdbcTemplate, "PLAN_A", "Plan Payer A", "Plan A PPO");
        Long planB = insertInsurancePlan(jdbcTemplate, "PLAN_B", "Plan Payer B", "Plan B HMO");

        SavedPlanDto saved = savedPlanService.save(userId, planA);
        assertThat(saved.insurancePlanId()).isEqualTo(planA);
        assertThat(saved.payerName()).isEqualTo("Plan Payer A");

        SavedPlanDto replaced = savedPlanService.save(userId, planB);
        assertThat(replaced.insurancePlanId()).isEqualTo(planB);
        assertThat(replaced.id()).isEqualTo(saved.id());

        Long rowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_saved_plan WHERE user_id = ?", Long.class, userId);
        assertThat(rowCount).isEqualTo(1L);
    }

    @Test
    void removingClearsTheSavedPlan() {
        Long userId = insertUser("plan-remove@example.com");
        Long planId = insertInsurancePlan(jdbcTemplate, "PLAN_REMOVE", "Plan Payer Remove", "Removable Plan");
        savedPlanService.save(userId, planId);

        savedPlanService.remove(userId);
        assertThat(savedPlanService.get(userId)).isNull();
    }

    @Test
    void savingAnUnknownPlanIs404() {
        Long userId = insertUser("plan-unknown@example.com");
        assertThatThrownBy(() -> savedPlanService.save(userId, 999_999_999L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(404);
    }

    private Long insertUser(String email) {
        AppUser user = appUserRepository.save(new AppUser(email, "irrelevant-hash", "Test User", Instant.now(), Instant.now()));
        return user.getId();
    }
}
