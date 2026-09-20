package com.njydsz.common.docs.security.pii.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.PiiFinding;
import com.njydsz.common.docs.enums.PiiType;

/**
 * {@link PhoneDetector} 单元测试。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@DisplayName("PhoneDetector 测试")
class PhoneDetectorTest {

  private PhoneDetector detector;

  @BeforeEach
  void setUp() {
    detector = new PhoneDetector();
  }

  @Nested
  @DisplayName("场景：正常检测")
  class WhenDetecting {

    @Test
    @DisplayName("含手机号的文本文档应检测出 PII 发现")
    void shouldDetectPhoneNumber() {
      DocumentContent content =
          DocumentContent.builder()
              .text("联系人：张三，电话 13812345678，地址北京")
              .build();

      var findings = detector.detect(content);

      assertThat(findings).isNotEmpty();
      PiiFinding finding = findings.get(0);
      assertThat(finding.getType()).isEqualTo(PiiType.PHONE);
      assertThat(finding.getMaskedValue()).contains("****");
      assertThat(finding.getConfidence()).isGreaterThan(java.math.BigDecimal.ZERO);
    }

    @Test
    @DisplayName("不含手机号的文本应返回空列表")
    void shouldReturnEmptyWhenNoPhone() {
      DocumentContent content =
          DocumentContent.builder().text("这是一段没有任何手机号的普通文本。").build();

      var findings = detector.detect(content);

      assertThat(findings).isEmpty();
    }
  }

  @Nested
  @DisplayName("场景：边界条件")
  class WhenBoundary {

    @Test
    @DisplayName("内容为 null 时应返回空列表")
    void shouldReturnEmptyWhenContentIsNull() {
      var findings = detector.detect(null);
      assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("文本为 null 时应返回空列表")
    void shouldReturnEmptyWhenTextIsNull() {
      DocumentContent content = DocumentContent.builder().text(null).build();
      var findings = detector.detect(content);
      assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("空文本应返回空列表")
    void shouldReturnEmptyWhenTextIsEmpty() {
      DocumentContent content = DocumentContent.builder().text("").build();
      var findings = detector.detect(content);
      assertThat(findings).isEmpty();
    }
  }

  @Nested
  @DisplayName("场景：脱敏验证")
  class WhenMasking {

    @Test
    @DisplayName("手机号脱敏应保留前 3 后 4 位")
    void shouldMaskCorrectly() {
      // 138****5678 形式
      String masked = detector.mask("13812345678");
      assertThat(masked).startsWith("138");
      assertThat(masked).endsWith("5678");
      assertThat(masked).contains("****");
    }

    @Test
    @DisplayName("null 或不足 7 位应返回 ****")
    void shouldReturnDefaultForInvalidPhone() {
      assertThat(detector.mask(null)).isEqualTo("****");
      assertThat(detector.mask("1234567")).isEqualTo("****");
    }
  }

  @Nested
  @DisplayName("场景：类型声明")
  class WhenType {

    @Test
    @DisplayName("getSupportedType 应返回 PHONE")
    void shouldSupportPhoneType() {
      assertThat(detector.getSupportedType()).isEqualTo(PiiType.PHONE);
    }
  }
}
