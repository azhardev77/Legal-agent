package com.tenderops.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * TC-QTE-01: quote normalization (rupee/Rs., dashes, curly quotes, line breaks,
 * case-insensitivity) verifies against page text and returns the EXACT RAW PDF SPAN.
 */
class QuoteVerifierTest {

  private static final char RUPEE = (char) 0x20B9;
  private static final char EN_DASH = (char) 0x2013;
  private static final char EM_DASH = (char) 0x2014;
  private static final char LSQUO = (char) 0x2018;
  private static final char RSQUO = (char) 0x2019;
  private static final char LDQUO = (char) 0x201C;
  private static final char RDQUO = (char) 0x201D;
  private static final char E_ACUTE_PRE = (char) 0x00E9;
  private static final char COMBINING_ACUTE = (char) 0x0301;

  @Test
  void rupeeSignMatchesRsAndReturnsRawSpan() {
    String page = "The turnover shall be not less than Rs. 100 Cr in total.";
    String model = "turnover shall be NOT LESS than " + RUPEE + " 100 cr in TOTAL";
    Optional<QuoteVerifier.VerifiedQuote> verified = QuoteVerifier.verify(page, model);
    assertThat(verified).isPresent();
    assertThat(verified.get().rawSpan())
        .isEqualTo("turnover shall be not less than Rs. 100 Cr in total");
    assertThat(page.substring(verified.get().rawStart(), verified.get().rawEnd()))
        .isEqualTo(verified.get().rawSpan());
  }

  @Test
  void rsWithoutDotMatchesRsWithDot() {
    String page = "Solvency of Rs 15 Cr minimum.";
    Optional<QuoteVerifier.VerifiedQuote> verified =
        QuoteVerifier.verify(page, "solvency of rs. 15 cr minimum");
    assertThat(verified).isPresent();
    assertThat(verified.get().rawSpan()).isEqualTo("Solvency of Rs 15 Cr minimum");
  }

  @Test
  void dashesUnify() {
    String page = "Period 01-Apr-2023 to 31-Mar-2024 inclusive.";
    String model = "period 01" + EN_DASH + "apr" + EM_DASH + "2023 to 31" + EN_DASH
        + "mar-2024 inclusive";
    Optional<QuoteVerifier.VerifiedQuote> verified = QuoteVerifier.verify(page, model);
    assertThat(verified).isPresent();
    assertThat(verified.get().rawSpan())
        .isEqualTo("Period 01-Apr-2023 to 31-Mar-2024 inclusive");
  }

  @Test
  void curlyQuotesUnify() {
    String page = "The bidder's \"best\" effort wins.";
    String model = "the bidder" + RSQUO + "s " + LDQUO + "best" + RDQUO + " effort wins";
    Optional<QuoteVerifier.VerifiedQuote> verified = QuoteVerifier.verify(page, model);
    assertThat(verified).isPresent();
    assertThat(verified.get().rawSpan())
        .isEqualTo("The bidder's \"best\" effort wins");
    String curlyPage = "The bidder" + LSQUO + "s effort wins.";
    Optional<QuoteVerifier.VerifiedQuote> reverse =
        QuoteVerifier.verify(curlyPage, "bidder's effort wins");
    assertThat(reverse).isPresent();
    assertThat(reverse.get().rawSpan()).isEqualTo("bidder" + LSQUO + "s effort wins");
  }

  @Test
  void lineBreaksCollapseToSpaces() {
    String page = "Clause 4.1\nThe average\nturnover shall\nbe high.";
    Optional<QuoteVerifier.VerifiedQuote> verified =
        QuoteVerifier.verify(page, "clause 4.1 the average turnover shall be high");
    assertThat(verified).isPresent();
    assertThat(verified.get().rawSpan())
        .isEqualTo("Clause 4.1\nThe average\nturnover shall\nbe high");
  }

  @Test
  void missingQuoteFails() {
    assertThat(QuoteVerifier.verify("Some page text here.", "a claim not present"))
        .isEmpty();
    assertThat(QuoteVerifier.verify("Some page text here.", "   ")).isEmpty();
    assertThat(QuoteVerifier.verify(null, "x")).isEmpty();
    assertThat(QuoteVerifier.verify("x", null)).isEmpty();
  }

  @Test
  void combiningMarksMatchPrecomposed() {
    String decomposedPage = "Visit the cafe" + COMBINING_ACUTE + " downtown.";
    String precomposedModel = "visit the caf" + E_ACUTE_PRE + " downtown";
    Optional<QuoteVerifier.VerifiedQuote> forward =
        QuoteVerifier.verify(decomposedPage, precomposedModel);
    assertThat(forward).isPresent();
    assertThat(forward.get().rawSpan()).isEqualTo("Visit the cafe" + COMBINING_ACUTE
        + " downtown");
    String precomposedPage = "Visit the caf" + E_ACUTE_PRE + " downtown.";
    String decomposedModel = "visit the cafe" + COMBINING_ACUTE + " downtown";
    Optional<QuoteVerifier.VerifiedQuote> reverse =
        QuoteVerifier.verify(precomposedPage, decomposedModel);
    assertThat(reverse).isPresent();
    assertThat(reverse.get().rawSpan()).isEqualTo("Visit the caf" + E_ACUTE_PRE
        + " downtown");
  }
}
