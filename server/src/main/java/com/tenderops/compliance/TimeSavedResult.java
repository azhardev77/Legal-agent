package com.tenderops.compliance;

import java.math.BigDecimal;

/** Honest time-saved arithmetic (section 6.9); baselines are labelled assumptions. */
public record TimeSavedResult(BigDecimal manualMinutes, BigDecimal assistedMinutes,
    BigDecimal percent) {
}
