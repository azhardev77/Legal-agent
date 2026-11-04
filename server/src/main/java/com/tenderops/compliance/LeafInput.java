package com.tenderops.compliance;

import java.math.BigDecimal;
import java.util.List;

/**
 * One evaluable leaf: either top-level requirement fields or one condition-tree leaf.
 * Tree leaves carry amountText only; the evaluator converts it via MoneyUtils (R6).
 */
public record LeafInput(String metric, String operator, String amountText,
    BigDecimal amountValue, Integer countValue, Integer lookbackYears,
    List<String> domainTags, String certificateName, Integer certificateLevel) {
}
