package com.njydsz.message.server.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.message.domain.dto.MsgCanaryDTO;
import com.njydsz.message.domain.repository.MsgCanaryRepository;
import com.njydsz.message.domain.vo.MsgCanaryVO;
import com.njydsz.message.server.service.config.CanaryExperimentService;

/**
 * 灰度实验服务实现
 *
 * <p>实现消息模板 A/B 对照实验的完整生命周期管理：实验创建、流量分桶、结果记录。
 *
 * <p><b>分桶策略：</b>基于请求标识（如 traceId / userId）哈希取模，保证同一请求始终落入同一桶， 分桶比例由灰度百分比决定。
 *
 * <p><b>实验组约定：</b>
 *
 * <ul>
 *   <li>CONTROL — 对照组，使用当前线上稳定版本
 *   <li>VARIANT — 实验组，使用灰度候选版本
 * </ul>
 *
 * <p><b>目标指标约定：</b>
 *
 * <ul>
 *   <li>DELIVERY_RATE — 送达率
 *   <li>READ_RATE — 阅读率
 *   <li>CLICK_RATE — 点击率
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CanaryExperimentService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanaryExperimentServiceImpl implements CanaryExperimentService {
  /** 哈希乘子 */
  private static final int HASH_MULTIPLIER = 31;


  /** 灰度实验 Repository */
  private final MsgCanaryRepository msgCanaryRepository;

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /** 默认总分桶数 */
  private static final int DEFAULT_BUCKET_TOTAL = 100;

  /** 对照组标识 */
  private static final String GROUP_CONTROL = "CONTROL";

  /** 实验组标识 */
  private static final String GROUP_VARIANT = "VARIANT";

  /** 实验运行状态 */
  private static final String STATUS_ACTIVE = "ACTIVE";

  /**
   * 创建 A/B 实验
   *
   * <p>根据模板编码、灰度配置和实验参数创建一个新的灰度实验。实验创建后状态为 ACTIVE， 可立即接收流量分桶。
   *
   * <p>canaryKey 格式：{@code canary_{templateCode}_{timestamp}}，保证全局唯一。
   * bucketSelected 按百分比自动计算（canaryPercent% * bucketTotal / 100）， 最大不超过 bucketTotal。
   *
   * @param templateCode 模板编码（不可为空）
   * @param experimentName 实验名称（不可为空）
   * @param canaryPercent 灰度流量百分比（1-100）
   * @param metricsGoal 目标指标（DELIVERY_RATE / READ_RATE / CLICK_RATE）
   * @return 实验唯一标识 canaryKey
   */
  @Override
  public String createExperiment(
      String templateCode, String experimentName, Integer canaryPercent, String metricsGoal) {

    String canaryKey = "canary_" + templateCode + "_" + System.currentTimeMillis();

    int bucketSelected = Math.min(canaryPercent, DEFAULT_BUCKET_TOTAL);

    MsgCanaryDTO canary = MsgCanaryDTO.builder()
        .id(String.valueOf(snowflakeIdGenerator.nextId()))
        .canaryKey(canaryKey)
        .experimentName(experimentName)
        .templateCode(templateCode)
        .bucketTotal(DEFAULT_BUCKET_TOTAL)
        .bucketSelected(bucketSelected)
        .percentage(canaryPercent)
        .experimentGroup(GROUP_VARIANT)
        .metricsGoal(metricsGoal)
        .status(STATUS_ACTIVE)
        .deleted(false)
        .createdAt(LocalDateTime.now())
        .build();

    msgCanaryRepository.save(canary);

    log.info("[CanaryExperiment] 创建实验成功: canaryKey={}, templateCode={}, percent={}",
        canaryKey, templateCode, canaryPercent);

    return canaryKey;
  }

  /**
   * 分配实验桶
   *
   * <p>根据请求标识和实验配置，将请求分配到 CONTROL 或 VARIANT 组。 同一 requestKey 在实验生命周期内始终返回相同的分配结果。
   *
   * <p>分桶算法：{@code Hash(requestKey) % bucketTotal}，结果桶号 {@code < bucketSelected} 归入 VARIANT 组， 否则归入 CONTROL 组。
   *
   * @param experimentId 实验唯一标识（即 canaryKey）
   * @param requestKey 请求标识（如 traceId 或 userId）
   * @return 实验组标识：CONTROL 或 VARIANT
   */
  @Override
  public String assignBucket(String experimentId, String requestKey) {

    MsgCanaryVO canary = selectByCanaryKey(experimentId);

    if (canary == null || !STATUS_ACTIVE.equals(canary.getStatus())) {
      log.info("[CanaryExperiment] 实验不存在或非ACTIVE: experimentId={}, 返回 CONTROL", experimentId);
      return GROUP_CONTROL;
    }

    int bucket = Math.floorMod(hashRequestKey(requestKey), canary.getBucketTotal());
    String assignedGroup = bucket < canary.getBucketSelected() ? GROUP_VARIANT : GROUP_CONTROL;

    log.info("[CanaryExperiment] 分桶结果: experimentId={}, bucket={}, group={}",
        experimentId, bucket, assignedGroup);

    return assignedGroup;
  }

  /**
   * 记录实验结果
   *
   * <p>记录指定实验组的目标指标数据，用于后续 A/B 效果对比分析。结果以结构化日志输出， 可由日志采集系统（如 ELK / Loki）聚合后生成实验报告。
   *
   * @param experimentId 实验唯一标识
   * @param experimentGroup 实验组（CONTROL / VARIANT）
   * @param metricValue 指标数值
   */
  @Override
  public void recordExperimentResult(String experimentId, String experimentGroup, double metricValue) {

    log.info("canary_record|experimentId={}|group={}|value={}", experimentId, experimentGroup,
        metricValue);
  }

  // ===== 私有方法 =====

  /**
   * 根据 canaryKey 查询实验（排除已删除）。
   *
   * @param canaryKey 实验唯一键
   * @return 实验 VO，不存在或已删除返回 null
   */
  private MsgCanaryVO selectByCanaryKey(String canaryKey) {
    return msgCanaryRepository.findByCanaryKey(canaryKey)
        .orElse(null);
  }

  /**
   * 对请求标识生成稳定哈希值。
   *
   * <p>使用 requestKey 的 UTF-8 字节数组取绝对值哈希，保证同一 key 始终映射到同一桶。 使用 {@link Math#floorMod} 处理负数取模。
   *
   * @param requestKey 请求标识
   * @return 非负哈希值
   */
  private int hashRequestKey(String requestKey) {
    if (requestKey == null) {
      return 0;
    }
    byte[] bytes = requestKey.getBytes(StandardCharsets.UTF_8);
    int hash = 1;
    for (byte b : bytes) {
      hash = HASH_MULTIPLIER * hash + b;
    }
    return Math.abs(hash);
  }
}
