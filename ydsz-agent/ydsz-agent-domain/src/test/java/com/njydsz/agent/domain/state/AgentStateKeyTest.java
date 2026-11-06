package com.njydsz.agent.domain.state;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AgentStateKey} 单元测试。
 *
 * <p>覆盖：存储键拼装、段清洗（分隔符/控制符/超长/空值）、扫描模式、键解析往返、equals/hashCode。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("AgentStateKey 状态分区键")
class AgentStateKeyTest {

  @Nested
  @DisplayName("存储键拼装")
  class StorageKeyTest {

    @Test
    @DisplayName("四段齐全时应生成标准键")
    void shouldBuildFullKey() {
      AgentStateKey key = AgentStateKey.of("session", "tenant-1", "user-1", "conv-1");
      assertThat(key.toStorageKey()).isEqualTo("ydsz:agent:session:tenant-1:user-1:conv-1");
    }

    @Test
    @DisplayName("null 段应回落占位符 default")
    void shouldUsePlaceholderForNullSegments() {
      AgentStateKey key = AgentStateKey.of("workspace", null, null, null);
      assertThat(key.toStorageKey()).isEqualTo("ydsz:agent:workspace:default:default:default");
      assertThat(key.getTenantId()).isEqualTo("default");
      assertThat(key.getUserId()).isEqualTo("default");
      assertThat(key.getConversationId()).isEqualTo("default");
    }

    @Test
    @DisplayName("ofBusiness 应把业务标识放入会话段")
    void shouldBuildBusinessKey() {
      AgentStateKey key = AgentStateKey.ofBusiness("checkpoint", "agent-code-1");
      assertThat(key.toStorageKey()).isEqualTo("ydsz:agent:checkpoint:default:default:agent-code-1");
    }

    @Test
    @DisplayName("带后缀键应追加后缀")
    void shouldAppendSuffix() {
      AgentStateKey key = AgentStateKey.of("session", "t", "u", "c");
      assertThat(key.toStorageKey("meta")).isEqualTo("ydsz:agent:session:t:u:c:meta");
    }

    @Test
    @DisplayName("后缀为 null/空白时应退回无后缀键")
    void shouldIgnoreBlankSuffix() {
      AgentStateKey key = AgentStateKey.of("session", "t", "u", "c");
      assertThat(key.toStorageKey(null)).isEqualTo(key.toStorageKey());
      assertThat(key.toStorageKey("  ")).isEqualTo(key.toStorageKey());
    }
  }

  @Nested
  @DisplayName("段清洗")
  class SanitizeTest {

    @Test
    @DisplayName("分隔符与通配符应被剔除")
    void shouldStripSeparatorAndWildcard() {
      AgentStateKey key = AgentStateKey.of("session", "a:b", "c*d", "e");
      assertThat(key.getTenantId()).isEqualTo("ab");
      assertThat(key.getUserId()).isEqualTo("cd");
      assertThat(key.toStorageKey()).doesNotContain("*").doesNotContain("::");
    }

    @Test
    @DisplayName("超长段应截断到 64 字符")
    void shouldTruncateLongSegment() {
      String longTenant = "x".repeat(100);
      AgentStateKey key = AgentStateKey.of("session", longTenant, "u", "c");
      assertThat(key.getTenantId()).hasSize(64);
    }

    @Test
    @DisplayName("空白段应回落占位符")
    void shouldUsePlaceholderForBlank() {
      AgentStateKey key = AgentStateKey.of("session", "  ", "\t", "c");
      assertThat(key.getTenantId()).isEqualTo("default");
      assertThat(key.getUserId()).isEqualTo("default");
    }
  }

  @Nested
  @DisplayName("扫描模式")
  class ScanPatternTest {

    @Test
    @DisplayName("分区扫描模式应以 * 结尾")
    void shouldBuildScanPattern() {
      AgentStateKey key = AgentStateKey.of("session", "t", "u", "c");
      assertThat(key.toScanPattern()).isEqualTo("ydsz:agent:session:t:u:c:*");
    }

    @Test
    @DisplayName("命名空间扫描模式应忽略后续段")
    void shouldBuildNamespaceScanPattern() {
      assertThat(AgentStateKey.namespaceScanPattern("checkpoint"))
          .isEqualTo("ydsz:agent:checkpoint:*");
    }
  }

  @Nested
  @DisplayName("键解析")
  class ParseTest {

    @Test
    @DisplayName("应能解析标准存储键")
    void shouldParseStandardKey() {
      Optional<AgentStateKey> parsed = AgentStateKey.parse("ydsz:agent:session:t:u:c");
      assertThat(parsed).isPresent();
      assertThat(parsed.get().getNamespace()).isEqualTo("session");
      assertThat(parsed.get().getTenantId()).isEqualTo("t");
      assertThat(parsed.get().getUserId()).isEqualTo("u");
      assertThat(parsed.get().getConversationId()).isEqualTo("c");
    }

    @Test
    @DisplayName("带后缀键应忽略后缀解析")
    void shouldIgnoreSuffixWhenParsing() {
      Optional<AgentStateKey> parsed = AgentStateKey.parse("ydsz:agent:session:t:u:c:meta");
      assertThat(parsed).isPresent();
      assertThat(parsed.get().getConversationId()).isEqualTo("c");
    }

    @Test
    @DisplayName("非 ydsz:agent: 前缀应返回 empty")
    void shouldReturnEmptyForWrongPrefix() {
      assertThat(AgentStateKey.parse("other:agent:session:t:u:c")).isEmpty();
    }

    @Test
    @DisplayName("段数不足应返回 empty")
    void shouldReturnEmptyForTooFewSegments() {
      assertThat(AgentStateKey.parse("ydsz:agent:session:t:u")).isEmpty();
    }

    @Test
    @DisplayName("null 应返回 empty")
    void shouldReturnEmptyForNull() {
      assertThat(AgentStateKey.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("存储键应能往返解析")
    void shouldRoundTrip() {
      AgentStateKey original = AgentStateKey.of("session", "t", "u", "c");
      Optional<AgentStateKey> parsed = AgentStateKey.parse(original.toStorageKey());
      assertThat(parsed).isPresent();
      assertThat(parsed.get()).isEqualTo(original);
    }
  }

  @Nested
  @DisplayName("相等性与字符串")
  class EqualityTest {

    @Test
    @DisplayName("四段相同应相等")
    void shouldEqualsWhenAllSegmentsEqual() {
      AgentStateKey a = AgentStateKey.of("session", "t", "u", "c");
      AgentStateKey b = AgentStateKey.of("session", "t", "u", "c");
      assertThat(a).isEqualTo(b);
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("任一段不同应不相等")
    void shouldNotEqualsWhenAnySegmentDiffers() {
      AgentStateKey base = AgentStateKey.of("session", "t", "u", "c");
      assertThat(base).isNotEqualTo(AgentStateKey.of("workspace", "t", "u", "c"));
      assertThat(base).isNotEqualTo(AgentStateKey.of("session", "t2", "u", "c"));
    }

    @Test
    @DisplayName("toString 应返回存储键")
    void shouldToStringReturnStorageKey() {
      AgentStateKey key = AgentStateKey.of("session", "t", "u", "c");
      assertThat(key.toString()).isEqualTo(key.toStorageKey());
    }

    @Test
    @DisplayName("namespace 为 null 应抛 NPE")
    void shouldThrowForNullNamespace() {
      assertThatThrownBy(() -> AgentStateKey.of(null, "t", "u", "c"))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
