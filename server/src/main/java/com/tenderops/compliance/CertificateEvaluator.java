package com.tenderops.compliance;

import com.tenderops.company.Certificate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Deterministic certificate evaluator (section 6.5), expiry against the deadline. */
public final class CertificateEvaluator {

  private static final String PROVENANCE = "company_certificates";

  private CertificateEvaluator() {
  }

  public static LeafResult evaluate(LeafInput input, List<Certificate> certs,
      EvaluationContext context) {
    if (input.certificateName() == null || input.certificateName().isBlank()) {
      return new TraceBuilder(Verdict.NEEDS_REVIEW, "CERT_NAME_MISSING",
          "required certificate name is missing", context).build(List.of(), PROVENANCE);
    }
    String wanted = TextNorms.alphaNumUpper(input.certificateName());
    List<Certificate> matches = new ArrayList<>();
    for (Certificate certificate : certs) {
      if (wanted.equals(TextNorms.alphaNumUpper(certificate.getName()))) {
        matches.add(certificate);
      }
    }
    if (matches.isEmpty()) {
      return new TraceBuilder(Verdict.UNKNOWN, "CERT_NOT_FOUND",
          "no " + input.certificateName() + " certificate on record", context)
          .build(List.of(), PROVENANCE);
    }
    List<Certificate> levelOk = new ArrayList<>();
    if (input.certificateLevel() != null) {
      for (Certificate certificate : matches) {
        if (certificate.getLevelNumber() != null
            && certificate.getLevelNumber() >= input.certificateLevel()) {
          levelOk.add(certificate);
        }
      }
      if (levelOk.isEmpty()) {
        return new TraceBuilder(Verdict.FAIL, "CERT_LEVEL_INSUFFICIENT",
            "certificate level below required level " + input.certificateLevel(),
            context).put("requiredLevel", String.valueOf(input.certificateLevel()))
            .put("actualLevel", maxLevel(matches))
            .build(ids(matches), PROVENANCE);
      }
    } else {
      levelOk.addAll(matches);
    }
    LocalDate deadline = context.deadlineDate();
    List<Certificate> valid = new ArrayList<>();
    List<Certificate> expired = new ArrayList<>();
    for (Certificate certificate : levelOk) {
      if (certificate.getExpiryDate() == null) {
        continue;
      }
      if (certificate.getExpiryDate().isBefore(deadline)) {
        expired.add(certificate);
      } else {
        valid.add(certificate);
      }
    }
    List<Certificate> validDocumented = new ArrayList<>();
    for (Certificate certificate : valid) {
      if (certificate.getDocumentId() != null) {
        validDocumented.add(certificate);
      }
    }
    if (!validDocumented.isEmpty()) {
      return new TraceBuilder(Verdict.PASS, "CERT_VALID",
          "expiry " + validDocumented.get(0).getExpiryDate() + " on/after deadline "
              + deadline,
          context).put("operands",
              List.of(validDocumented.get(0).getExpiryDate().toString()))
          .put("expiryDate", validDocumented.get(0).getExpiryDate().toString())
          .put("deadlineDate", deadline.toString())
          .put("comparison",
              validDocumented.get(0).getExpiryDate() + " >= " + deadline)
          .build(ids(validDocumented), PROVENANCE);
    }
    if (!valid.isEmpty()) {
      return new TraceBuilder(Verdict.NEEDS_REVIEW, "MISSING_SUPPORTING_DOCUMENT",
          "valid certificate has no supporting document", context)
          .put("expiryDate", valid.get(0).getExpiryDate().toString())
          .put("deadlineDate", deadline.toString())
          .build(ids(valid), PROVENANCE);
    }
    if (!expired.isEmpty()) {
      return new TraceBuilder(Verdict.FAIL, "CERT_EXPIRED",
          "expiry " + expired.get(0).getExpiryDate() + " before deadline " + deadline,
          context).put("expiryDate", expired.get(0).getExpiryDate().toString())
          .put("deadlineDate", deadline.toString())
          .build(ids(expired), PROVENANCE);
    }
    return new TraceBuilder(Verdict.NEEDS_REVIEW, "EXPIRY_MISSING",
        "certificate has no expiry date", context).build(ids(levelOk), PROVENANCE);
  }

  private static String maxLevel(List<Certificate> certs) {
    Integer max = null;
    for (Certificate certificate : certs) {
      if (certificate.getLevelNumber() != null
          && (max == null || certificate.getLevelNumber() > max)) {
        max = certificate.getLevelNumber();
      }
    }
    return max == null ? "NONE" : String.valueOf(max);
  }

  private static List<Long> ids(List<Certificate> certs) {
    List<Long> ids = new ArrayList<>();
    for (Certificate certificate : certs) {
      ids.add(certificate.getId());
    }
    return ids;
  }
}
