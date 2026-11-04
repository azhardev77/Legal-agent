package com.tenderops.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** TC-MON-01, TC-MON-02: MoneyUtils parses raw INR amount text exactly (ground truth section 13). */
class MoneyUtilsTest {

  private static final String RUPEE = "\u20B9";

  private static void parses(String input, String expected) {
    assertThat(MoneyUtils.parse(input)).isEqualByComparingTo(new BigDecimal(expected));
    assertThat(MoneyUtils.parse(input).scale()).isEqualTo(4);
  }

  @Test
  void tcMon01CoreUnits() {
    parses("100 Cr", "1000000000.0000");
    parses("Rs. 25 Lakhs", "2500000.0000");
    parses("Rs. 1,00,00,000", "10000000.0000");
    parses("45 Crores", "450000000.0000");
    parses("12.5 Cr", "125000000.0000");
  }

  @Test
  void tcMon01RupeeSignVariants() {
    parses(RUPEE + "100 Cr", "1000000000.0000");
    parses(RUPEE + "50,000", "50000.0000");
    parses(RUPEE + "25 Lakhs", "2500000.0000");
  }

  @Test
  void tcMon01UnparseableThrowsTypedException() {
    assertThatThrownBy(() -> MoneyUtils.parse("abc")).isInstanceOf(MoneyParseException.class);
    assertThatThrownBy(() -> MoneyUtils.parse("")).isInstanceOf(MoneyParseException.class);
    assertThatThrownBy(() -> MoneyUtils.parse(null)).isInstanceOf(MoneyParseException.class);
    assertThatThrownBy(() -> MoneyUtils.parse("Rs. Cr")).isInstanceOf(MoneyParseException.class);
  }

  @Test
  void tcMon02PunctuationAndCase() {
    parses("Rs. 1.2 Cr.", "12000000.0000");
    parses("Rs 25 lakh", "2500000.0000");
    parses(RUPEE + "50,000", "50000.0000");
    parses("rs. 1 cr", "10000000.0000");
    parses("10 LACS", "1000000.0000");
    parses("2 Lac", "200000.0000");
  }

  @Test
  void tcMon02NegativeThrows() {
    assertThatThrownBy(() -> MoneyUtils.parse("-Rs. 5 Cr"))
        .isInstanceOf(MoneyParseException.class);
    assertThatThrownBy(() -> MoneyUtils.parse("Rs. -5 Cr"))
        .isInstanceOf(MoneyParseException.class);
  }

  @Test
  void goldenThresholds() {
    parses("Rs 100 Cr", "1000000000.0000");
    parses("Rs 30 Cr", "300000000.0000");
    parses("Rs. 110 crore", "1100000000.0000");
    parses("Rs. 20 crore", "200000000.0000");
    parses("Rs 50,000", "50000.0000");
  }

  @Test
  void stringValueIsPlainScale4() {
    assertThat(MoneyUtils.toStringValue(new BigDecimal("1000000000")))
        .isEqualTo("1000000000.0000");
  }
}
