package com.njydsz.message.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.message.domain.vo.ChannelHealthVO;
import com.njydsz.message.domain.vo.SystemHealthVO;
import com.njydsz.message.server.service.core.MessageHealthService;

/**
 * 系统健康检查 Controller。
 *
 * <p>提供消息模块运行时健康状态的查询 HTTP API，包含整体健康摘要、各通道熔断器状态、滑动窗口失败计数等运维关键指标，供管理后台监控面板和运维巡检系统消费。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/health/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>系统整体健康</b>：{@code GET /health} — 返回 UP / DEGRADED / DOWN 三态及通道摘要
 *   <li><b>通道详细状态</b>：{@code GET /health/channels} — 返回各通道熔断器状态、失败计数、失败率
 * </ul>
 *
 * <p><b>健康判定规则：</b>
 *
 * <ul>
 *   <li>任一通道熔断器状态为 OPEN → 整体状态 DEGRADED
 *   <li>所有通道熔断器状态为 CLOSED → 整体状态 UP
 *   <li>无可用通道 → 整体状态 DOWN
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see MessageHealthService 消息健康检查服务
 */
@Tag(name = "系统健康检查", description = "消息模块运行时健康状态监控")
@RestController
@RequestMapping("/api/v1/message/health")
@RequiredArgsConstructor
public class SystemHealthController {

  /** 消息模块健康检查服务 */
  private final MessageHealthService messageHealthService;

  /**
   * 获取系统整体健康状态。
   *
   * <p>汇总各通道熔断器状态，按规则判定整体健康度，返回 UP（正常）/ DEGRADED（降级）/ DOWN（不可用）三态。
   *
   * @return 统一响应结果，包含整体状态和通道摘要
   */
  @Operation(summary = "系统整体健康状态")
  @AuthApiPermission("MESSAGE_LOG_VIEW")
  @GetMapping
  public BaseResponse<SystemHealthVO> getSystemHealth() {
    return BaseResponse.success(messageHealthService.getSystemHealth());
  }

  /**
   * 获取各通道详细健康状态列表。
   *
   * <p>返回每个已注册通道的熔断器状态（CLOSED / OPEN / HALF_OPEN）、滑动窗口内失败计数、总请求计数和计算失败率。
   *
   * @return 统一响应结果，包含通道健康状态列表
   */
  @Operation(summary = "各通道详细健康状态")
  @AuthApiPermission("MESSAGE_LOG_VIEW")
  @GetMapping("/channels")
  public BaseResponse<java.util.List<ChannelHealthVO>> getChannelHealths() {
    return BaseResponse.success(messageHealthService.getChannelHealths());
  }
}
