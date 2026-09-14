package com.njydsz.agent.domain.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AgentStateKey} 单元测试。
 *
 * <p>覆盖四段式键生成、键段清洗、反解析与扫描模式。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
class AgentStateKeyTest {

  /** 标准键前缀 */
  private static final String KEY_PREFIX = "ydsz:agent:";

  /**
   * 正常分区键应生成四段式存储键。
   */
  @Test
  @DisplayName("toStorageKey 生成四段式键")
  void toStorageKeyShouldGenerateFourSegments() {
    AgentStateKey key = AgentStateKey.of("session", "t1", "u1", "c1");

    assertEquals(KEY_PREFIX + "session:t1:u1:c1", key.toStorageKey());
  }

  /**
   * null 租户 / 用户 / 会话应回退为 default 占位符。
   */
  @Test
  @DisplayName("空段应回退为 default 占位符")
  void nullSegmentsShouldFallbackToDefault() {
    AgentStateKey key = AgentStateKey.of("workspace", null, null, null);

    assertEquals(KEY_PREFIX + "workspace:default:default:default", key.toStorageKey());
  }

  /**
   * 键段中的分隔符应被清洗，防止越界或注入。
   */
  @Test
  @DisplayName("键段应剔除分隔符与控制字符")
  void segmentsShouldBeSanitized() {
    AgentStateKey key = AgentStateKey.of("session", "t:1", "u*1", "c 1");

    assertEquals(KEY_PREFIX + "session:t1:u1:c1", key.toStorageKey());
  }

  /**
   * 超过最大长度的段应被截断。
   */
  @Test
  @DisplayName("超长段应被截断")
  void longSegmentsShouldBeTruncated() {
    String longId = "a".repeat(100);
    AgentStateKey key = AgentStateKey.of("session", longId, "u1", "c1");

    assertEquals(64, key.getTenantId().length());
    assertEquals(KEY_PREFIX + key.getNamespace() + ":" + key.getTenantId() + ":u1:c1", key.toStorageKey());
  }

  /**
   * 带后缀的存储键应在四段后追加清洗后的后缀。
   */
  @Test
  @DisplayName("toStorageKey(suffix) 应追加清洗后的后缀")
  void toStorageKeyWithSuffixShouldAppendSanitizedSuffix() {
    AgentStateKey key = AgentStateKey.of("session", "t1", "u1", "c1");

    assertEquals(KEY_PREFIX + "session:t1:u1:c1:meta", key.toStorageKey("meta"));
    assertEquals(KEY_PREFIX + "session:t1:u1:c1", key.toStorageKey(" "));
  }

  /**
   * 本分区的扫描模式应以通配符结尾。
   */
  @Test
  @DisplayName("toScanPattern 生成本分区扫描模式")
  void toScanPatternShouldEndWithWildcard() {
    AgentStateKey key = AgentStateKey.of("checkpoint", "t1", "u1", "c1");

    assertEquals(KEY_PREFIX + "checkpoint:t1:u1:c1:*", key.toScanPattern());
  }

  /**
   * 命名空间级扫描模式应只包含命名空间与通配符。
   */
  @Test
  @DisplayName("namespaceScanPattern 生成命名空间级扫描模式")
  void namespaceScanPatternShouldContainNamespaceAndWildcard() {
    assertEquals(KEY_PREFIX + "session:*", AgentStateKey.namespaceScanPattern("session"));
  }

  /**
   * 合法存储键应被正确反解析。
   */
  @Test
  @DisplayName("parse 应反解析合法存储键")
  void parseShouldDecodeValidStorageKey() {
    String storageKey = KEY_PREFIX + "session:t1:u1:c1";
    Optional<AgentStateKey> parsed = AgentStateKey.parse(storageKey);

    assertTrue(parsed.isPresent());
    assertEquals("session", parsed.get().getNamespace());
    assertEquals("t1", parsed.get().getTenantId());
    assertEquals("u1", parsed.get().getUserId());
    assertEquals("c1", parsed.get().getConversationId());
  }

  /**
   * 带后缀的存储键反解析时应忽略后缀。
   */
  @Test
  @DisplayName("parse 应忽略后缀段")
  void parseShouldIgnoreSuffix() {
    String storageKey = KEY_PREFIX + "session:t1:u1:c1:extra:suffix";
    Optional<AgentStateKey> parsed = AgentStateKey.parse(storageKey);

    assertTrue(parsed.isPresent());
    assertEquals("c1", parsed.get().getConversationId());
  }

  /**
   * 非本模块前缀或段数不足时应返回 empty。
   */
  @Test
  @DisplayName("parse 应拒绝非法键")
  void parseShouldRejectInvalidKeys() {
    assertFalse(AgentStateKey.parse(null).isPresent());
    assertFalse(AgentStateKey.parse("other:session:t1:u1:c1").isPresent());
    assertFalse(AgentStateKey.parse(KEY_PREFIX + "session:t1:u1").isPresent());
  }

  /**
   * 相同分区参数的键应相等。
   */
  @Test
  @DisplayName("equals 与 hashCode 应基于四段语义")
  void equalsShouldBeBasedOnSegments() {
    AgentStateKey key1 = AgentStateKey.of("session", "t1", "u1", "c1");
    AgentStateKey key2 = AgentStateKey.of("session", "t1", "u1", "c1");
    AgentStateKey key3 = AgentStateKey.of("workspace", "t1", "u1", "c1");

    assertEquals(key1, key2);
    assertEquals(key1.hashCode(), key2.hashCode());
    assertFalse(key1.equals(key3));
  }
}
