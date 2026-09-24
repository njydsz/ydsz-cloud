package com.njydsz.message.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.message.server.service.config.CanaryExperimentService;

/**
 * 灰度实验 Controller
 *
 * <p>提供消息模板 A/B 对照实验的<b>实验管理与流量分桶</b> HTTP API，是 {@code ydsz-message} 模块「灰度发布」的入口。
 *
 * <p><b>接口路径：</b>{@code /api/message/canary/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>创建实验</b>：{@code POST /experiment} — 基于模板编码和灰度配置创建 A/B 实验
 *   <li><b>分配实验桶</b>：{@code GET /assign} — 根据请求标识分配到 CONTROL / VARIANT 组
 * </ul>
 *
 * <p><b>分桶策略：</b>基于请求标识（如 traceId / userId）哈希取模，保证同一请求始终落入同一桶。
 *
 * <p><b>安全特性：</b>写接口通过 {@code @AuthApiPermission} 校验 {@code MESSAGE_TEMPLATE_EDIT} 权限码， 与模板操作同级权限。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see CanaryExperimentService 灰度实验服务
 */
@Tag(name = "灰度实验", description = "消息模板 A/B 对照实验管理")
@Slf4j
@ApiVersion("26.09.01")
@RestController
@RequestMapping("/message/canary")
@RequiredArgsConstructor
public class CanaryController {

  /** 灰度实验服务 */
  private final CanaryExperimentService canaryExperimentService;

  /**
   * 创建灰度实验
   *
   * <p>基于指定模板编码创建 A/B 对照实验，按 {@code canaryPercent} 将流量分入 CONTROL（对照组）或 VARIANT（实验组）；
   * 实验期间按 {@code metricsGoal} 收集指标，用于评估新模板效果。
   *
   * @param templateCode 模板编码（不可为空，对应已注册的模板）
   * @param experimentName 实验名称（不可为空，用于标识和展示）
   * @param canaryPercent 灰度流量百分比（1-100，表示 VARIANT 组流量占比）
   * @param metricsGoal 目标指标（DELIVERY_RATE / READ_RATE / CLICK_RATE，用于实验效果评估）
   * @return 实验唯一标识 {@code canaryKey}，用于后续分桶查询
   */
  @Operation(summary = "创建实验")
  @AuthApiPermission(apiCodes = "MESSAGE_TEMPLATE_EDIT")
  @PostMapping("/experiment")
  public YdszResponse<String> createExperiment(
      @RequestParam String templateCode,
      @RequestParam String experimentName,
      @RequestParam Integer canaryPercent,
      @RequestParam String metricsGoal) {

    String canaryKey = canaryExperimentService.createExperiment(
        templateCode, experimentName, canaryPercent, metricsGoal);

    return YdszResponse.success(canaryKey);
  }

  /**
   * 分配实验桶
   *
   * <p>根据请求标识（traceId / userId）哈希取模，保证同一请求标识始终落入同一桶，
   * 返回 CONTROL（对照组）或 VARIANT（实验组）标识。
   *
   * @param experimentId 实验唯一标识（由 {@link #createExperiment} 返回的 canaryKey）
   * @param requestKey 请求标识（如 traceId 或 userId，用于稳定分桶）
   * @return 实验组标识：{@code CONTROL}（对照组）或 {@code VARIANT}（实验组）
   */
  @Operation(summary = "分配实验桶")
  @AuthApiPermission(apiCodes = "MESSAGE_TEMPLATE_EDIT")
  @GetMapping("/assign")
  public YdszResponse<String> assignBucket(
      @RequestParam String experimentId,
      @RequestParam String requestKey) {

    String group = canaryExperimentService.assignBucket(experimentId, requestKey);

    return YdszResponse.success(group);
  }
}
