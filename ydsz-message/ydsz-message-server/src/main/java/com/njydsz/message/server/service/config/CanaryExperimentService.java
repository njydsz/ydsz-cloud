package com.njydsz.message.server.service.config;

import com.njydsz.message.domain.dto.TemplateCanaryDTO;

/**
 * 灰度实验服务接口
 *
 * <p>提供 A/B 对照实验的核心能力，支撑消息模板灰度发布过程中的实验管理、流量分桶与结果记录。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>实验创建</b>：{@link #createExperiment} — 基于模板编码和灰度配置创建 A/B 实验
 *   <li><b>流量分桶</b>：{@link #assignBucket} — 根据实验配置将请求分配到 CONTROL / VARIANT 组
 *   <li><b>结果记录</b>：{@link #recordExperimentResult} — 记录实验指标数据用于效果对比
 * </ul>
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
 * @since 26.09.01
 * @see TemplateCanaryDTO
 * @see com.njydsz.message.domain.vo.MsgCanaryVO
 */
public interface CanaryExperimentService {

    /**
     * 创建 A/B 实验
     *
     * <p>根据模板编码、灰度配置和实验参数创建一个新的灰度实验。实验创建后状态为 ACTIVE， 可立即接收流量分桶。
     *
     * @param templateCode 模板编码（不可为空）
     * @param experimentName 实验名称（不可为空）
     * @param canaryPercent 灰度流量百分比（1-100）
     * @param metricsGoal 目标指标（DELIVERY_RATE / READ_RATE / CLICK_RATE）
     * @return 实验唯一标识
     */
    String createExperiment(String templateCode, String experimentName, Integer canaryPercent, String metricsGoal);

    /**
     * 分配实验桶
     *
     * <p>根据请求标识和实验配置，将请求分配到 CONTROL 或 VARIANT 组。 同一 requestKey 在实验生命周期内始终返回相同的分配结果。
     *
     * @param experimentId 实验唯一标识
     * @param requestKey 请求标识（如 traceId 或 userId）
     * @return 实验组标识：CONTROL 或 VARIANT
     */
    String assignBucket(String experimentId, String requestKey);

    /**
     * 记录实验结果
     *
     * <p>记录指定实验组的目标指标数据，用于后续 A/B 效果对比分析。
     *
     * @param experimentId 实验唯一标识
     * @param experimentGroup 实验组（CONTROL / VARIANT）
     * @param metricValue 指标数值
     */
    void recordExperimentResult(String experimentId, String experimentGroup, double metricValue);
}
