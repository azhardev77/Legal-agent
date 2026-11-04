package com.tenderops.visual;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * TC-VIS-05: no visual-region model field, accessor, or serialized payload claims stamp or
 * signature validity/authenticity. (Repo-wide wording is re-checked by grep at M9/CL-13.)
 */
class ValidityClaimTest {

  private static final List<String> BANNED = List.of("validity", "authentic", "genuine",
      "forged", "forgery", "verified", "legitimate", "tamper");

  private static final List<Class<?>> MODEL_CLASSES = List.of(
      VisualRegion.class,
      VisualRegionDetector.Detection.class,
      VisionRegionReader.StructuredTable.class,
      AiCallLog.class,
      SamOutputParser.SamBox.class);

  @Test
  void noModelMemberSuggestsValidity() {
    for (Class<?> type : MODEL_CLASSES) {
      for (Field field : type.getDeclaredFields()) {
        assertThat(field.getName().toLowerCase())
            .as("field %s#%s", type.getSimpleName(), field.getName())
            .doesNotContain(BANNED.toArray(new String[0]));
      }
      for (Method method : type.getDeclaredMethods()) {
        assertThat(method.getName().toLowerCase())
            .as("method %s#%s", type.getSimpleName(), method.getName())
            .doesNotContain(BANNED.toArray(new String[0]));
      }
    }
  }

  @Test
  void serializedRegionCarriesNoValidityWording() throws Exception {
    VisualRegion region = new VisualRegion(7L, VisualRegion.OFFICIAL_STAMP,
        new BigDecimal("0.100000"), new BigDecimal("0.100000"), new BigDecimal("0.200000"),
        new BigDecimal("0.200000"), new BigDecimal("0.6000"), VisualRegion.SOURCE_FALLBACK);
    region.setRegionText("MSDC DEMO SEAL");
    region.setContentSource(VisualRegion.CONTENT_GEOMETRIC);
    String json =
        new ObjectMapper().writeValueAsString(region).toLowerCase();
    assertThat(json).doesNotContain(BANNED.toArray(new String[0]));
  }
}
