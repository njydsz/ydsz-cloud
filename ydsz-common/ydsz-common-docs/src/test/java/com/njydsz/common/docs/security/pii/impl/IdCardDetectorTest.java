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
 * {@link IdCardDetector} 单元测试。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@DisplayName("IdCardDetector 测试")
class IdCardDetectorTest {

  private IdCardDetector detector;

  @BeforeEach
  void setUp() {
    detector = new IdCardDetector();
  }

  @Nested
  @DisplayName("场景：正常检测")
  class WhenDetecting {

    @Test
    @DisplayName("含身份证号的文本文档应检测出 PII 发现")
    void shouldDetectIdCardNumber() {
      // 18 位身份证号：前 6 位区域码 + 8 位出生日期 + 3 位顺序码 + 1 位校验码
      DocumentContent content =
          DocumentContent.builder()
              .text("员工信息：姓名 李四，身份证号 110101199003077654，部门技术部")
              .build();

      var findings = detector.detect(content);

      assertThat(findings).isNotEmpty();
      PiiFinding finding = findings.get(0);
      assertThat(finding.getType()).isEqualTo(PiiType.ID_CARD);
      // 文档场景脱敏：保留前 6 位行政区划码 + 后 4 位
      assertThat(finding.getMaskedValue()).startsWith("110101");
    }

    @Test
    @DisplayName("不含身份证号的文本应返回空列表")
    void shouldReturnEmptyWhenNoIdCard() {
      DocumentContent content =
          DocumentContent.builder().text("这是一段不含身份证号的文本。").build();

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
  }

  @Nested
  @DisplayName("场景：脱敏验证（保留首尾）")
  class WhenMasking {

    @Test
    @DisplayName("身份证号脱敏应保留前 6 位与后 4 位")
    void shouldPreservePrefixAndSuffix() {
      // 110101********7654 形式
      String masked = detector.mask("110101199003077654");
      assertThat(masked).startsWith("110101");
      assertThat(masked).endsWith("7654");
      assertThat(masked).contains("********");
    }

    @Test
    @DisplayName("null 或不足 10 位应返回 ****")
    void shouldReturnDefaultForInvalidId() {
      assertThat(detector.mask(null)).isEqualTo("****");
      assertThat(detector.mask("123456789")).isEqualTo("****");
    }
  }

  @Nested
  @DisplayName("场景：类型声明")
  class WhenType {

    @Test
    @DisplayName("getSupportedType 应返回 ID_CARD")
    void shouldSupportIdCardType() {
      assertThat(detector.getSupportedType()).isEqualTo(PiiType.ID_CARD);
    }
  }
}
