package com.tenderops.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** TC-CUR-01 helper: foreign-currency signals in raw amount text (R15). */
class MoneyCurrencyTest {

  @Test
  void detectsForeignCurrencies() {
    assertThat(MoneyUtils.currencyOf("USD 100 Cr")).isEqualTo("USD");
    assertThat(MoneyUtils.currencyOf("$100")).isEqualTo("USD");
    assertThat(MoneyUtils.currencyOf("100 US dollars")).isEqualTo("USD");
    assertThat(MoneyUtils.currencyOf("EUR 50")).isEqualTo("EUR");
    assertThat(MoneyUtils.currencyOf("50 euros")).isEqualTo("EUR");
    assertThat(MoneyUtils.currencyOf("GBP 50")).isEqualTo("GBP");
    assertThat(MoneyUtils.currencyOf("50 pounds")).isEqualTo("GBP");
    assertThat(MoneyUtils.currencyOf("100 " + (char) 0x20AC)).isEqualTo("EUR");
    assertThat(MoneyUtils.currencyOf("100 " + (char) 0x00A3)).isEqualTo("GBP");
  }

  @Test
  void defaultsToInr() {
    assertThat(MoneyUtils.currencyOf("Rs. 100 Cr")).isEqualTo("INR");
    assertThat(MoneyUtils.currencyOf("INR 100")).isEqualTo("INR");
    assertThat(MoneyUtils.currencyOf((char) 0x20B9 + "100 Cr")).isEqualTo("INR");
    assertThat(MoneyUtils.currencyOf("100 Cr")).isEqualTo("INR");
    assertThat(MoneyUtils.currencyOf(null)).isEqualTo("INR");
  }
}
