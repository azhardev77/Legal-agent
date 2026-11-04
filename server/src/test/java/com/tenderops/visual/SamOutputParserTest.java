package com.tenderops.visual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Provisional SAM grammar parsing (TC-VIS-03 parser half). */
class SamOutputParserTest {

  @Test
  void parsesBoxWithConfidenceAndMaskAttachment() {
    List<SamOutputParser.SamBox> boxes = SamOutputParser.parse(
        "box[10,20,110,220,c=0.95],mask[h=200,w=100,enc=b85,data=QUJD]",
        200, 400);
    assertThat(boxes).hasSize(1);
    SamOutputParser.SamBox box = boxes.get(0);
    assertThat(box.x0()).isEqualTo(0.05);
    assertThat(box.y0()).isEqualTo(0.05);
    assertThat(box.x1()).isEqualTo(0.55);
    assertThat(box.y1()).isEqualTo(0.55);
    assertThat(box.confidence()).isEqualTo(0.95);
    assertThat(box.maskRef()).isEqualTo("mask[h=200,w=100,enc=b85,data=QUJD]");
  }

  @Test
  void boxWithoutConfidenceDefaultsAndClamps() {
    List<SamOutputParser.SamBox> boxes =
        SamOutputParser.parse("box[-50,-50,500,500]", 200, 200);
    assertThat(boxes).hasSize(1);
    assertThat(boxes.get(0).confidence()).isEqualTo(0.90);
    assertThat(boxes.get(0).x0()).isEqualTo(0.0);
    assertThat(boxes.get(0).y0()).isEqualTo(0.0);
    assertThat(boxes.get(0).x1()).isEqualTo(1.0);
    assertThat(boxes.get(0).y1()).isEqualTo(1.0);
  }

  @Test
  void orphanMaskIsIgnoredAndMultipleBoxesParse() {
    List<SamOutputParser.SamBox> boxes = SamOutputParser.parse(
        "mask[h=1,w=1,enc=x,data=y],box[0,0,10,10],box[20,20,30,30,c=0.5]", 100, 100);
    assertThat(boxes).hasSize(2);
    assertThat(boxes.get(0).maskRef()).isNull();
    assertThat(boxes.get(1).confidence()).isEqualTo(0.5);
  }

  @Test
  void malformedOutputsThrowSamFormatException() {
    assertThatThrownBy(() -> SamOutputParser.parse("", 100, 100))
        .isInstanceOf(SamFormatException.class);
    assertThatThrownBy(() -> SamOutputParser.parse("box[0,0,10,10", 100, 100))
        .isInstanceOf(SamFormatException.class);
    assertThatThrownBy(() -> SamOutputParser.parse("box[10,10,0,0]", 100, 100))
        .isInstanceOf(SamFormatException.class);
    assertThatThrownBy(() -> SamOutputParser.parse("box[0,0,10,10,c=1.5]", 100, 100))
        .isInstanceOf(SamFormatException.class);
    assertThatThrownBy(() -> SamOutputParser.parse("blob[0,0,10,10]", 100, 100))
        .isInstanceOf(SamFormatException.class);
    assertThatThrownBy(
        () -> SamOutputParser.parse("mask[h=1,w=1,enc=x,data=y]", 100, 100))
        .isInstanceOf(SamFormatException.class);
    assertThatThrownBy(() -> SamOutputParser.parse("mask[h=1,w=1]", 100, 100))
        .isInstanceOf(SamFormatException.class);
  }
}
