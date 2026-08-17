package com.docfitai.backend.insurance;

/** Plan type is never inferred from arbitrary text -- only set when a source reliably states it. */
public enum PlanType {
    HMO,
    PPO,
    EPO,
    POS,
    OTHER
}
