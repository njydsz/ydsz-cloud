package com.njydsz.literule.server.core;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.njydsz.common.util.security.DigestUtils;
import com.njydsz.literule.api.RuleContext;

/**
 * 轻量级缓存键构建器（P1-1 性能优化）
 *
 * <p>替代 {@link EvaluationResultCache} 中基于全量字符串拼接的缓存键方案，将键长从 O(总 facts 序列化长度) 降至固定 ~80 字符：
 *
 * <ul>
 *   <li><b>前缀</b>：{@code scenario|tenantId|environment}，标识维度
 *   <li><b>内容哈希</b>：facts 按 key 排序后序列化的 SHA-256 摘要（十六进制，64 字符）
 * </ul>
 *
 * <p>优势：
 *
 * <ul>
 *   <li>键长固定，不受 facts 数量/值长度影响，内存占用稳定
 *   <li>{@link String#hashCode()} 缓存后，equals 比较仅需 ~80 字符
 *   <li>SHA-256 碰撞概率极低（~2^-128），可安全用于缓存场景
 * </ul>
 *
 * <p>线程安全：{@link MessageDigest} 实例为方法局部变量，无共享状态。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public final class CacheKeyBuilder {

    /** 每个事实的估算 Key 长度（用于预分配 StringBuilder） */
  private static final int KEY_INIT_CAPACITY_MULTIPLIER = 16;

  private CacheKeyBuilder() {
    // 工具类不可实例化
  }

  /** 分隔符（避免与 facts 内容冲突） */
  private static final char SEPARATOR = '|';

  /**
   * 为评估上下文构建缓存键
   *
   * <p>格式：{@code scenario|tenantId|environment|sha256Hex}
   *
   * @param context 规则上下文
   * @return 缓存键（固定长度约 80 字符 + 维度前缀）
   */
  public static String buildKey(RuleContext context) {
    Map<String, Object> facts = context.getFacts();
    // 按 key 排序保证相同 facts 产生相同键
    SortedMap<String, Object> sortedFacts = new TreeMap<>(facts);
    byte[] factsBytes = serializeFacts(sortedFacts);
    String hashHex = DigestUtils.sha256Hex(factsBytes);
    return context.getScenario()
        + SEPARATOR
        + context.getTenantId()
        + SEPARATOR
        + context.getEnvironment()
        + SEPARATOR
        + hashHex;
  }

  /**
   * 序列化排序后的 facts 为 UTF-8 字节数组
   *
   * <p>格式：{@code key1=value1;key2=value2;...}，与原始实现兼容的序列化方式。
   *
   * @param sortedFacts 按 key 排序的 facts
   * @return UTF-8 字节数组
   */
  private static byte[] serializeFacts(SortedMap<String, Object> sortedFacts) {
    StringBuilder sb = new StringBuilder(sortedFacts.size() * KEY_INIT_CAPACITY_MULTIPLIER);
    sortedFacts.forEach(
        (key, value) -> {
          sb.append(key).append('=');
          sb.append(value != null ? value.toString() : "null");
          sb.append(';');
        });
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  }
