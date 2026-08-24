package com.heraim.eco.dto;

import com.heraim.eco.model.Decision;

public record DecisionRequest(
    String flagId,
    Decision decision
) {
}
