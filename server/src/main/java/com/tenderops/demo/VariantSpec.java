package com.tenderops.demo;

import java.util.List;

/** Parameter set driving Variant B generation (proves nothing is hardcoded to the golden tender). */
public record VariantSpec(
    String title,
    String reference,
    String authority,
    String estimatedText,
    String deadlineText,
    List<ClauseSpec> clauses) {

  public record ClauseSpec(String ref, String text) {}

  /** Variant B parameter set, exact texts from MASTER_PROMPT section 5. */
  public static VariantSpec variantB() {
    return new VariantSpec(
        "Demonstration Tender Document (Variant B)",
        "VB/2026/001",
        "Variant Tendering Authority (DEMO)",
        "Rs 50 Cr",
        "20 December 2026, 17:00 IST",
        List.of(
            new ClauseSpec("3.1",
                "Bidders shall have a minimum average annual turnover of Rs. 110 crore "
                    + "during the last three audited financial years."),
            new ClauseSpec("3.2",
                "Net worth of the bidder shall be at least Rs. 20 crore as per the last "
                    + "audited balance sheet."),
            new ClauseSpec("3.3",
                "The bidder shall have completed at least one (1) similar project "
                    + "(Smart City or Highway) of value Rs. 30 crore or more during the "
                    + "last five years."),
            new ClauseSpec("3.4",
                "Bidder shall possess a valid ISO 9001 certificate on the date of bid "
                    + "submission.")));
  }
}
