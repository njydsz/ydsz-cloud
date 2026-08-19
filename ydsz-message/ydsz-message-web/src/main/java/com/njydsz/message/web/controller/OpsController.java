package com.njydsz.message.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.message.domain.vo.BloomFilterStatsVO;
import com.njydsz.message.domain.vo.CacheStatsVO;
import com.njydsz.message.server.consumer.BloomFilterDeduplicator;
import com.njydsz.message.server.template.cache.CachedTemplateEngine;

/**
 * 运维诊断 Controller。
 *
 * <p>提供消息模块运维操作能力的 HTTP API，包含模板缓存管理（查询统计、失效清除）和 BloomFilter 去重过滤器状态查询，供管理后台运维面板和自动化运维系统消费。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/ops/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>模板缓存统计</b>：{@code GET /ops/template-cache/stats} — 返回 Caffeine 缓存条目数、命中率、淘汰次数等指标
 *   <li><b>模板缓存清除</b>：{@code DELETE /ops/template-cache?template=xxx} — 失效指定模板的编译缓存
 *   <li><b>模板缓存全清</b>：{@code DELETE /ops/template-cache/all} — 清空所有模板编译缓存
 *   <li><b>BloomFilter 统计</b>：{@code GET /ops/bloomfilter/stats} — 返回 BloomFilter 容量、误判率、窗口年龄等指标
 * </ul>
 *
 * <p><b>安全要求：</b>所有接口均需高权限认证（{@code MESSAGE_LOG_VIEW} 或 {@code MESSAGE_TEMPLATE_EDIT}），防止越权操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CachedTemplateEngine 模板引擎缓存
 * @see BloomFilterDeduplicator 消息去重过滤器
 */
@Slf4j
@Tag(name = "运维诊断", description = "消息模块运维操作接口（高权限）")
@RestController
@RequestMapping("/api/v1/message/ops")
@RequiredArgsConstructor
public class OpsController {

  /** 带 AST 缓存的模板引擎 */
  private final CachedTemplateEngine cachedTemplateEngine;

  /** 基于 BloomFilter 的消息去重过滤器 */
  private final BloomFilterDeduplicator bloomFilterDeduplicator;

  /**
   * 获取模板缓存统计信息。
   *
   * <p>返回 Caffeine 缓存的运行时指标，包含当前缓存条目数、命中次数、未命中次数、命中率和淘汰次数。
   *
   * @return 统一响应结果，包含缓存统计信息
   */
  @Operation(summary = "模板缓存统计")
  @AuthApiPermission("MESSAGE_LOG_VIEW")
  @GetMapping("/template-cache/stats")
  public BaseResponse<CacheStatsVO> getTemplateCacheStats() {
    com.github.benmanes.caffeine.cache.stats.CacheStats stats = cachedTemplateEngine.caffeineCacheStats();
    CacheStatsVO vo = CacheStatsVO.builder()
        .size(cachedTemplateEngine.cacheSize())
        .hitCount(stats.hitCount())
        .missCount(stats.missCount())
        .hitRate(stats.hitRate())
        .evictionCount(stats.evictionCount())
        .build();
    return BaseResponse.success(vo);
  }

  /**
   * 清除指定模板缓存。
   *
   * <p>主动失效指定模板的编译后 AST 缓存，通常在模板内容更新后调用，确保下次渲染使用最新编译结果。
   *
   * @param template 模板内容
   * @return 统一响应结果
   */
  @Operation(summary = "清除模板缓存")
  @AuthApiPermission("MESSAGE_TEMPLATE_EDIT")
  @DeleteMapping("/template-cache")
  public BaseResponse<Void> evictTemplateCache(@RequestParam String template) {
    cachedTemplateEngine.evictCache(template);
    return BaseResponse.success(null);
  }

  /**
   * 清空所有模板缓存。
   *
   * <p>清除全部模板 AST 缓存并重置命中/未命中计数器。此操作会导致后续请求重新编译模板，短时间内 CPU 负载升高，请谨慎使用。
   *
   * @return 统一响应结果
   */
  @Operation(summary = "清空所有模板缓存")
  @AuthApiPermission("MESSAGE_TEMPLATE_EDIT")
  @DeleteMapping("/template-cache/all")
  public BaseResponse<Void> clearTemplateCache() {
    cachedTemplateEngine.clearCache();
    return BaseResponse.success(null);
  }

  /**
   * 获取 BloomFilter 统计信息。
   *
   * <p>返回消息去重 BloomFilter 的运行状态，包含预期插入条目数、当前误判率、是否为主窗口和窗口已运行秒数。
   *
   * @return 统一响应结果，包含 BloomFilter 统计信息
   */
  @Operation(summary = "BloomFilter 统计")
  @AuthApiPermission("MESSAGE_LOG_VIEW")
  @GetMapping("/bloomfilter/stats")
  public BaseResponse<BloomFilterStatsVO> getBloomFilterStats() {
    BloomFilterStatsVO vo = BloomFilterStatsVO.builder()
        .expectedInsertions(bloomFilterDeduplicator.getExpectedInsertions())
        .fpp(bloomFilterDeduplicator.getFalsePositiveProbability())
        .primary(true)
        .windowAgeSeconds(bloomFilterDeduplicator.getWindowAgeSeconds())
        .build();
    return BaseResponse.success(vo);
  }
}
