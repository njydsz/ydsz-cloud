package com.njydsz.message.web.controller.core;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.infra.entity.MsgTraceDO;
import com.njydsz.message.server.service.core.MessageTraceService;

/**
 * 消息端到端追踪（Trace）Controller。
 *
 * <p>提供<b>消息全链路追踪</b>的 HTTP API，是 P0-2「可观测性」的核心入口。 每条消息在生命周期各阶段（创建 / 入队 / 消费 / 发送 / 回执 / 撤回）都会写入
 * {@code ydsz_msg_trace} 表， 通过 {@code msgTraceService} 聚合查询完整的轨迹。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/trace/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>按 msgId 查询</b>：{@code GET /msg/{msgId}} — 某条消息的完整轨迹
 *   <li><b>按 traceId 查询</b>：{@code GET /trace/{traceId}} — 某次追踪链的完整轨迹（跨服务链路）
 *   <li><b>按业务查询</b>：{@code GET /biz} — 按 (bizType, bizId) 查询某业务单据触发的全部消息轨迹
 * </ul>
 *
 * <p><b>轨迹节点：</b>{@code MsgTrace.traceStage} 包括：
 *
 * <ul>
 *   <li>{@code CREATED}：消息创建
 *   <li>{@code ENQUEUED}：投递到 RocketMQ
 *   <li>{@code CONSUMED}：MQ 消费成功
 *   <li>{@code SENT}：调用供应商完成发送
 *   <li>{@code DELIVERED}：服务商回执「已送达」
 *   <li>{@code READ}：用户「已读」
 *   <li>{@code CLICKED}：用户「已点击」
 *   <li>{@code RECALLED}：消息撤回
 *   <li>{@code FAILED}：任意阶段失败
 * </ul>
 *
 * <p><b>与 SkyWalking / OpenTelemetry 的关系：</b>本追踪表记录的是<b>业务级</b>轨迹（消息生命周期）， 与系统 APM（{@code
 * ydsz:traceId}）通过 {@code traceId} 关联，互为补充：
 *
 * <ul>
 *   <li>本表：业务视角（消息从发送到被消费的端到端流转）
 *   <li>APM：系统视角（微服务调用链、DB 查询耗时、HTTP 请求）
 * </ul>
 *
 * <p><b>典型场景：</b>
 *
 * <ul>
 *   <li>客服查某用户某条消息为何未送达 → {@code GET /msg/{msgId}}
 *   <li>研发排查某条消息在某服务卡了多久 → {@code GET /trace/{traceId}}
 *   <li>运营审计某业务单据触发的全部消息 → {@code GET /biz?bizType=ORDER&bizId=12345}
 * </ul>
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>只读接口，仅启用 {@code @AuthApiPermission} 权限校验
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#MESSAGE_LOG_VIEW} 权限码
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.core.MessageTraceService 消息追踪服务
 * @see com.njydsz.message.infra.entity.MsgTraceDO 追踪实体
 */
@Tag(name = "消息追踪", description = "消息端到端全链路追踪")
@RestController
@RequestMapping("/api/v1/message/trace")
@RequiredArgsConstructor
public class MessageTraceController {

  /** 消息追踪服务 */
  private final MessageTraceService messageTraceService;

  /**
   * 按消息 ID 查询完整轨迹。
   *
   * @param msgId 消息 ID
   * @return 统一响应结果，包含轨迹列表
   */
  @Operation(summary = "按消息 ID 查询轨迹")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
  @GetMapping("/msg/{msgId}")
  public BaseResponse<List<MsgTraceDO>> getByMsgId(@PathVariable String msgId) {
    return BaseResponse.success(messageTraceService.getTraceByMsgId(msgId));
  }

  /**
   * 按链路追踪 ID 查询完整轨迹。
   *
   * @param traceId 链路追踪 ID
   * @return 统一响应结果，包含轨迹列表
   */
  @Operation(summary = "按链路追踪 ID 查询轨迹")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
  @GetMapping("/trace/{traceId}")
  public BaseResponse<List<MsgTraceDO>> getByTraceId(@PathVariable String traceId) {
    return BaseResponse.success(messageTraceService.getTraceByTraceId(traceId));
  }

  /**
   * 按业务类型和单据 ID 查询轨迹。
   *
   * @param bizType 业务类型
   * @param bizId 单据 ID
   * @return 统一响应结果，包含轨迹列表
   */
  @Operation(summary = "按业务类型+单据 ID 查询轨迹")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
  @GetMapping("/biz")
  public BaseResponse<List<MsgTraceDO>> getByBiz(
      @RequestParam String bizType, @RequestParam String bizId) {
    return BaseResponse.success(messageTraceService.getTraceByBiz(bizType, bizId));
  }
}
