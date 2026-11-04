package com.tenderops.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * General clause-pattern extractor (fallback; aiMode=FALLBACK). Handles varied wording
 * ("not less than", "minimum", "at least", "Rs.", "crore", "lakh"), amendment phrases,
 * and metadata lines. Never reads golden/expected data; unrecognized marked clauses
 * become metric OTHER (NEEDS_REVIEW downstream, never PASS, never silently dropped).
 */
@Component
public class RuleBasedFallbackExtractor implements RequirementExtractor {

  private static final char RUPEE_CHAR = (char) 0x20B9;

  private static final double PATTERN_CONFIDENCE = 0.85;
  private static final double OTHER_CONFIDENCE = 0.50;

  private static final Pattern CLAUSE_MARKER =
      Pattern.compile("(?m)(?:^|\\n)\\s*Clause\\s+(\\d+(?:\\.\\d+)*)\\b");
  private static final Pattern SUBSTITUTE =
      Pattern.compile("(?i)\\bis\\s+substituted\\s+by\\b");
  private static final Pattern SUBSTITUTE_PREFIX =
      Pattern.compile("(?i)^\\s*(the\\s+following\\s*:?\\s*)?");
  private static final Pattern DELETED = Pattern.compile("(?i)\\bis\\s+deleted\\b");
  private static final Pattern INSERTED = Pattern.compile("(?i)\\bis\\s+inserted\\b");
  private static final Pattern DEADLINE_EXTENDED =
      Pattern.compile("(?i)deadline[^.\\n]*?extended\\s+to\\s+([^.\\n]+)");

  private static final Pattern TURNOVER = Pattern.compile("(?i)average\\s+annual\\s+turnover");
  private static final Pattern NET_WORTH = Pattern.compile("(?i)net\\s+worth");
  private static final Pattern SOLVENCY = Pattern.compile("(?i)bank\\s+solvency\\s+certificate");
  private static final Pattern RULE_805040 = Pattern.compile("(?i)80\\s*/\\s*50\\s*/\\s*40");
  private static final Pattern COUNT_PROJECTS = Pattern.compile(
      "(?i)(?:at\\s+least\\s+)?(one|two|three|four|five|six|seven|eight|nine|ten|\\d+)"
          + "\\s*(?:\\(\\s*\\d+\\s*\\)\\s*)?similar\\s+projects?\\b");
  private static final Pattern LOOKBACK = Pattern.compile(
      "(?i)(?:last|during\\s+the\\s+last|over\\s+the\\s+last|in\\s+the\\s+last)\\s+"
          + "(one|two|three|four|five|six|seven|eight|nine|ten|\\d+)\\s+"
          + "(?:audited\\s+)?(?:financial\\s+)?years\\b");
  private static final Pattern SMART_CITY = Pattern.compile("(?i)smart\\s*-?\\s*city");
  private static final Pattern HIGHWAY = Pattern.compile("(?i)highway");
  private static final Pattern CMMI = Pattern.compile("(?i)CMMI\\s+Level\\s+(\\d+)");
  private static final Pattern ISO = Pattern.compile("(?i)ISO\\s*[- ]?\\s*(\\d{4,5})");
  private static final Pattern OR_JOIN =
      Pattern.compile("(?i)\\bor\\b|\\beither\\b.+\\bor\\b");

  private static final Pattern DESIRABLE =
      Pattern.compile("(?i)desirable|preferred|optional|bonus\\s+marks");
  private static final Pattern NON_MANDATORY = Pattern.compile("(?i)non-mandatory");
  private static final Pattern MANDATORY_CUES =
      Pattern.compile("(?i)\\bshall\\b|\\bmust\\b|\\brequired\\b|\\bmandatory\\b");

  private static final Pattern REFERENCE = Pattern.compile("(?i)Tender\\s+No\\.\\s*([A-Z0-9/._-]+)");
  private static final Pattern ESTIMATED =
      Pattern.compile("(?i)Estimated\\s+Contract\\s+Value\\s*:?\\s*");
  private static final Pattern EMD =
      Pattern.compile("(?i)(?:Earnest\\s+Money\\s+Deposit\\s*(?:\\(EMD\\))?|EMD)\\s*:?\\s*");
  private static final Pattern FEE = Pattern.compile("(?i)Tender\\s+fee\\s*:?\\s*");
  private static final Pattern DEADLINE_LINE = Pattern.compile(
      "(?i)(?:bid\\s+submission\\s+)?deadline\\s*:?\\s*"
          + "(\\d{1,2}\\s+\\w+\\s+\\d{4}(?:\\s*,?\\s*\\d{1,2}:\\d{2}\\s*(?:IST|UTC|GMT)?)?)");

  private static final char EURO_CHAR = (char) 0x20AC;
  private static final char POUND_CHAR = (char) 0x00A3;

  private static final Pattern AMOUNT = Pattern.compile(
      "((?:Rs\\.?|INR|" + RUPEE_CHAR + "|US\\$|USD|\\$|EUR|" + EURO_CHAR + "|GBP|"
          + POUND_CHAR + ")?\\s*\\d[\\d,]*\\.?\\d*"
          + "\\s*(?:crores?|cr|lakhs?|lacs?)?)",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern AMOUNT_PREFIX = Pattern.compile(
      "(?i)^(?:Rs\\.?|INR|" + RUPEE_CHAR + "|US\\$|USD|\\$|EUR|" + EURO_CHAR + "|GBP|"
          + POUND_CHAR + ")\\b");
  private static final Pattern AMOUNT_UNIT =
      Pattern.compile("(?i)(crores?|cr|lakhs?|lacs?)\\.?$");

  private static final List<String> GTE_BEFORE =
      List.of("not less than", "not below", "no less than", "at least", "minimum");
  private static final List<String> LTE_CUES = List.of("not more than", "not exceed", "at most",
      "or less", "less than", "below", "under", "maximum of", "up to");
  private static final List<String> GTE_AFTER = List.of("or more", "or above", "or higher");
  private static final List<String> GT_CUES =
      List.of("more than", "greater than", "exceed", "above", "over");
  private static final List<String> EQ_CUES = List.of("equal to", "exactly");

  private static final Map<String, Integer> NUMBER_WORDS = new LinkedHashMap<>();

  static {
    String[] words =
        {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};
    for (int i = 0; i < words.length; i++) {
      NUMBER_WORDS.put(words[i], i + 1);
    }
  }

  @Override
  public Extraction.Result extract(String sourceDocument, int sourcePage, String pageText) {
    String text = pageText == null ? "" : pageText;
    List<Extraction.RequirementItem> requirements = new ArrayList<>();
    List<int[]> markers = new ArrayList<>();
    List<String> refs = new ArrayList<>();
    Matcher marker = CLAUSE_MARKER.matcher(text);
    while (marker.find()) {
      markers.add(new int[] {marker.start(), marker.end()});
      refs.add(marker.group(1));
    }
    for (int i = 0; i < markers.size(); i++) {
      int start = markers.get(i)[0];
      int end = i + 1 < markers.size() ? markers.get(i + 1)[0] : text.length();
      String candidate = text.substring(start, end).trim();
      if (!candidate.isEmpty()) {
        requirements.addAll(parseCandidate(refs.get(i), candidate));
      }
    }
    return new Extraction.Result(extractMetadata(text), requirements);
  }

  private List<Extraction.RequirementItem> parseCandidate(String ref, String candidate) {
    Matcher substitute = SUBSTITUTE.matcher(candidate);
    if (substitute.find()) {
      String newText = SUBSTITUTE_PREFIX.matcher(
          candidate.substring(substitute.end())).replaceFirst("").trim();
      Extraction.RequirementItem item = classify(ref, newText, candidate);
      return List.of(withAmendment(item, "SUBSTITUTE", ref));
    }
    if (DELETED.matcher(candidate).find()) {
      return List.of(markerItem(ref, candidate, "DELETE", ref));
    }
    Matcher inserted = INSERTED.matcher(candidate);
    if (inserted.find()) {
      String newText = candidate.substring(inserted.end()).replaceFirst("(?i)^\\s*:?\\s*", "");
      Extraction.RequirementItem item = classify(ref, newText, candidate);
      return List.of(withAmendment(item, "INSERT", ref));
    }
    return List.of(classify(ref, candidate, candidate));
  }

  private Extraction.RequirementItem classify(String ref, String text, String quote) {
    boolean desirable = DESIRABLE.matcher(text).find() || NON_MANDATORY.matcher(text).find();
    String withoutNon = NON_MANDATORY.matcher(text).replaceAll(" ");
    boolean forced = MANDATORY_CUES.matcher(withoutNon).find();
    boolean mandatory = !desirable || forced;
    boolean ambiguous = desirable && forced;

    if (SOLVENCY.matcher(text).find()) {
      String amount = firstAmountAfter(text, SOLVENCY.matcher(text));
      if (amount != null) {
        return leaf(ref, "FINANCIAL", mandatory, ambiguous, "SOLVENCY_CERTIFICATE",
            operatorAround(text, amount), amount, null, null, null, null, null, quote);
      }
      return other(ref, "FINANCIAL", mandatory, ambiguous, quote);
    }
    if (TURNOVER.matcher(text).find()) {
      String amount = firstAmount(text);
      Integer lookback = lookbackYears(text);
      if (amount != null) {
        return leaf(ref, "FINANCIAL", mandatory, ambiguous, "AVG_ANNUAL_TURNOVER",
            operatorAround(text, amount), amount, null, lookback, null, null, null, quote);
      }
      return other(ref, "FINANCIAL", mandatory, ambiguous, quote);
    }
    if (NET_WORTH.matcher(text).find()) {
      String amount = firstAmount(text);
      if (amount != null) {
        return leaf(ref, "FINANCIAL", mandatory, ambiguous, "NET_WORTH",
            operatorAround(text, amount), amount, null, null, null, null, null, quote);
      }
      return other(ref, "FINANCIAL", mandatory, ambiguous, quote);
    }
    ConditionNode projects = projectTree(text);
    if (projects != null) {
      return treeItem(ref, "TECHNICAL", mandatory, ambiguous, projects, quote);
    }
    List<ConditionNode> certs = certificateLeaves(text);
    if (certs.size() == 1) {
      ConditionNode leaf = certs.get(0);
      return leaf(ref, "CERTIFICATION", mandatory, ambiguous, "CERTIFICATE_VALID", "GTE",
          null, null, null, null, leaf.certificateName(), leaf.certificateLevel(), quote);
    }
    if (certs.size() > 1) {
      String join = OR_JOIN.matcher(text).find() ? "OR" : "AND";
      return treeItem(ref, "CERTIFICATION", mandatory, ambiguous,
          ConditionNode.branch(join, certs), quote);
    }
    return other(ref, "OTHER", mandatory, ambiguous, quote);
  }

  private ConditionNode projectTree(String text) {
    Matcher count = COUNT_PROJECTS.matcher(text);
    Integer countValue = null;
    String amount = null;
    String operator = "GTE";
    List<String> domains = domainsOf(text);
    Integer lookback = lookbackYears(text);
    if (count.find()) {
      countValue = numberOf(count.group(1));
      amount = firstAmountAfter(text, count.end());
      if (amount == null) {
        return null;
      }
      operator = operatorAround(text, amount);
    }
    boolean has805040 = RULE_805040.matcher(text).find();
    if (countValue == null && !has805040) {
      return null;
    }
    List<ConditionNode> branches = new ArrayList<>();
    if (countValue != null) {
      branches.add(ConditionNode.leaf("SIMILAR_PROJECTS_COUNT_VALUE", operator, amount,
          countValue, lookback, domains, null, null));
    }
    if (has805040) {
      branches.add(ConditionNode.leaf("SIMILAR_PROJECTS_80_50_40", "GTE", null, null,
          lookback, domains, null, null));
    }
    if (branches.size() == 1) {
      return branches.get(0);
    }
    return ConditionNode.branch("OR", branches);
  }

  private List<ConditionNode> certificateLeaves(String text) {
    List<ConditionNode> leaves = new ArrayList<>();
    Matcher cmmi = CMMI.matcher(text);
    while (cmmi.find()) {
      int level;
      try {
        level = Integer.parseInt(cmmi.group(1));
      } catch (NumberFormatException e) {
        continue;
      }
      leaves.add(ConditionNode.leaf("CERTIFICATE_VALID", "GTE", null, null, null, null,
          "CMMI", level));
    }
    Matcher iso = ISO.matcher(text);
    while (iso.find()) {
      leaves.add(ConditionNode.leaf("CERTIFICATE_VALID", "GTE", null, null, null, null,
          "ISO_" + iso.group(1), null));
    }
    return leaves;
  }

  private Extraction.RequirementItem leaf(String ref, String category, boolean mandatory,
      boolean ambiguous, String metric, String operator, String amount, Integer count,
      Integer lookback, List<String> domains, String certName, Integer certLevel,
      String quote) {
    return new Extraction.RequirementItem(ref, category, mandatory, ambiguous, metric,
        operator, amount, count, lookback, domains, certName, certLevel, null, null, quote,
        PATTERN_CONFIDENCE, "NONE", null);
  }

  private Extraction.RequirementItem treeItem(String ref, String category, boolean mandatory,
      boolean ambiguous, ConditionNode tree, String quote) {
    if (!tree.isBranch()) {
      return leaf(ref, category, mandatory, ambiguous, tree.metric(), tree.operator(),
          tree.amountText(), tree.countValue(), tree.lookbackYears(), tree.domainTags(),
          tree.certificateName(), tree.certificateLevel(), quote);
    }
    return new Extraction.RequirementItem(ref, category, mandatory, ambiguous, "OTHER",
        "GTE", null, null, null, null, null, null, tree.canonical(), null, quote,
        PATTERN_CONFIDENCE, "NONE", null);
  }

  private Extraction.RequirementItem other(String ref, String category, boolean mandatory,
      boolean ambiguous, String quote) {
    return new Extraction.RequirementItem(ref, category, mandatory, ambiguous, "OTHER",
        "GTE", null, null, null, null, null, null, null, null, quote, OTHER_CONFIDENCE,
        "NONE", null);
  }

  private Extraction.RequirementItem markerItem(String ref, String quote, String action,
      String target) {
    return new Extraction.RequirementItem(ref, "OTHER", true, false, "OTHER", "GTE", null,
        null, null, null, null, null, null, null, quote, OTHER_CONFIDENCE, action, target);
  }

  private Extraction.RequirementItem withAmendment(Extraction.RequirementItem item,
      String action, String target) {
    return new Extraction.RequirementItem(item.clauseRef(), item.category(),
        item.mandatory(), item.mandatoryAmbiguous(), item.metric(), item.operator(),
        item.amountText(), item.countValue(), item.lookbackYears(), item.domainTags(),
        item.certificateName(), item.certificateLevel(), item.conditionTree(),
        item.sourceRegionId(), item.originalQuote(), item.confidence(), action, target);
  }

  private Extraction.TenderBlock extractMetadata(String text) {
    String reference = group1(REFERENCE.matcher(text));
    String estimated = amountAfterLabel(text, ESTIMATED);
    String emd = amountAfterLabel(text, EMD);
    String fee = amountAfterLabel(text, FEE);
    String deadline = group1(DEADLINE_LINE.matcher(text));
    if (deadline == null) {
      Matcher extended = DEADLINE_EXTENDED.matcher(text);
      if (extended.find()) {
        deadline = extended.group(1).trim();
      }
    }
    if (reference == null && estimated == null && emd == null && fee == null
        && deadline == null) {
      return new Extraction.TenderBlock(null, null, null, null, null);
    }
    return new Extraction.TenderBlock(reference, estimated, emd, fee, deadline);
  }

  private String group1(Matcher matcher) {
    return matcher.find() ? matcher.group(1).trim() : null;
  }

  private String amountAfterLabel(String text, Pattern label) {
    Matcher matcher = label.matcher(text);
    if (!matcher.find()) {
      return null;
    }
    return firstAmountAfter(text, matcher.end());
  }

  private String firstAmount(String text) {
    return firstAmountAfter(text, 0);
  }

  private String firstAmountAfter(String text, int from) {
    Matcher matcher = AMOUNT.matcher(text);
    matcher.region(from, text.length());
    while (matcher.find()) {
      String candidate = matcher.group(1).trim().replaceAll("[.\\s]+$", "");
      if (isRealAmount(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private String firstAmountAfter(String text, Matcher anchor) {
    if (anchor.find()) {
      return firstAmountAfter(text, anchor.end());
    }
    return firstAmount(text);
  }

  private boolean isRealAmount(String candidate) {
    return AMOUNT_PREFIX.matcher(candidate).find()
        || AMOUNT_UNIT.matcher(candidate).find()
        || candidate.indexOf(',') >= 0;
  }

  private String operatorAround(String text, String amount) {
    int at = text.indexOf(amount);
    if (at < 0) {
      return "GTE";
    }
    String before = text.substring(Math.max(0, at - 60), at).replaceAll("\\s+", " ")
        .toLowerCase(Locale.ROOT);
    String after = text.substring(Math.min(text.length(), at + amount.length()),
        Math.min(text.length(), at + amount.length() + 20)).replaceAll("\\s+", " ")
        .toLowerCase(Locale.ROOT);
    for (String cue : GTE_BEFORE) {
      if (before.contains(cue)) {
        return "GTE";
      }
    }
    for (String cue : GTE_AFTER) {
      if (after.contains(cue)) {
        return "GTE";
      }
    }
    for (String cue : EQ_CUES) {
      if (before.contains(cue)) {
        return "EQ";
      }
    }
    for (String cue : LTE_CUES) {
      if (before.contains(cue)) {
        return "LTE";
      }
    }
    for (String cue : GT_CUES) {
      if (before.contains(cue)) {
        return "GT";
      }
    }
    if (after.contains("or less")) {
      return "LTE";
    }
    return "GTE";
  }

  private Integer lookbackYears(String text) {
    Matcher matcher = LOOKBACK.matcher(text);
    if (matcher.find()) {
      return numberOf(matcher.group(1));
    }
    return null;
  }

  private List<String> domainsOf(String text) {
    List<String> domains = new ArrayList<>();
    if (SMART_CITY.matcher(text).find()) {
      domains.add("SMART_CITY");
    }
    if (HIGHWAY.matcher(text).find()) {
      domains.add("HIGHWAY");
    }
    return domains;
  }

  private Integer numberOf(String word) {
    if (word == null) {
      return null;
    }
    String lower = word.trim().toLowerCase(Locale.ROOT);
    if (NUMBER_WORDS.containsKey(lower)) {
      return NUMBER_WORDS.get(lower);
    }
    try {
      return Integer.parseInt(lower);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
