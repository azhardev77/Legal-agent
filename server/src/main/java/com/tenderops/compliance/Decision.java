package com.tenderops.compliance;

import java.util.List;

/**
 * Bid decision over mandatory requirements only. Approval status stays
 * PENDING_HUMAN_APPROVAL until a person signs off (the system never auto-submits).
 */
public record Decision(String recommendation, String approvalStatus, List<String> warnings) {
}
