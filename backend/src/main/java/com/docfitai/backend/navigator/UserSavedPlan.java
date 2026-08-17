package com.docfitai.backend.navigator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * An explicit, opt-in reference to one of DocFit's own public payer/plan records
 * (CLAUDE.md "Saved Plan Model" / "Do Not Store Member Information"). Never stores a member ID,
 * policy number, group number, date of birth, or SSN -- there is no column for any of that here,
 * by design. One row per user (MVP: a single saved plan, not a list).
 */
@Entity
@Table(name = "user_saved_plan")
public class UserSavedPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "insurance_plan_id", nullable = false)
    private Long insurancePlanId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserSavedPlan() {
    }

    public UserSavedPlan(Long userId, Long insurancePlanId, Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.insurancePlanId = insurancePlanId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getInsurancePlanId() {
        return insurancePlanId;
    }

    public void setInsurancePlanId(Long insurancePlanId) {
        this.insurancePlanId = insurancePlanId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
