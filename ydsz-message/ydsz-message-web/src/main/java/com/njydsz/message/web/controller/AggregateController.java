package com.njydsz.message.web.controller.batch;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.jdbc.support.PageResponses;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.message.domain.vo.MsgAggregateVO;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.infra.entity.MsgAggregate;
import com.njydsz.message.server.service.batch.AggregateService;

/**
 * 消息聚合批次（Aggregate）Controller。
 *
 * <p>提供<b>消息聚合批次查询与强制刷新</b>的 HTTP API。 聚合批次指对同一聚合组（{@code group}）+ 同一接收人（{@code
 * receiver}）的相似消息按时间窗口合并， 避免短时间内对同一用户轰炸通知（例如"订单状态变更"被聚合成"您有 3 条订单更新"）。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/aggregate/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>分页查询</b>：{@code GET /page} — 按时间 / 状态 / 接收人等过滤聚合批次列表
 *   <li><b>指定聚合组刷新</b>：{@code POST /flush} — 按 (group, receiver) 强制刷新，绕过等待窗口立即下发
 *   <li><b>到期批次刷新</b>：{@code POST /flushDue} — 立即处理所有到期的聚合批次（通常由 cron 调度）
 * </ul>
 *
 * <p><b>聚合窗口：</b>由 {@code ydsz.message.aggregate.window-seconds} 配置（默认 60s），
 * 窗口内到达的相似消息进入同一批次；窗口结束后由 {@code AggregateFlushScheduler} 调度器扫描到期批次。
 *
 * <p><b>典型场景：</b>
 *
 * <ul>
 *   <li>系统监控告警：1 分钟内 100 条 ERROR 日志被聚合成「[系统告警] 过去 1 分钟新增 100 条 ERROR」
 *   <li>订单状态变更：同一订单的多次状态变更被聚合成单条通知
 *   <li>评论回复：同一帖子的多条新评论被聚合成单条通知
 * </ul>
 *
 * <p><b>强制刷新：</b>{@code /flush} 接口用于紧急场景下立即下发聚合消息， 不等待时间窗口结束；返回刷新的消息数量。
 *
 * <p><b>多租户隔离：</b>所有操作按 {@code tenantId} 隔离，跨租户聚合不可见。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口（flush）启用 {@link Idempotent} 5s 防重
 *   <li>写接口（flush）启用 {@link RateLimit} 50 QPS 限流
 *   <li>写接口（flush）启用 {@link Audit} 审计日志（异步持久化）
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#MESSAGE_AGGREGATE_REFRESH} 权限码
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.batch.AggregateService 聚合批次服务
 * @see com.njydsz.message.domain.entity.batch.MsgAggregate 聚合批次实体
 */
@Tag(name = "聚合批次", description = "消息聚合批次查询与刷新")
@Slf4j
@RestController
@RequestMapping("/api/v1/message/aggregate")
@RequiredArgsConstructor
public class AggregateController {

  /** 聚合批次服务 */
  private final AggregateService aggregateService;

  /**
   * 分页查询聚合批次列表。
   *
   * @param query 分页查询参数
   * @return 统一响应结果，包含聚合批次分页数据
   */
  @Operation(summary = "聚合批次分页")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_AGGREGATE_LIST)
  @GetMapping("/page")
  public YdszResponse<PageResponse<List<MsgAggregateVO>>> page(PageQuery query) {
    Page<MsgAggregate> page = aggregateService.page(query);
    return YdszResponse.success(PageResponses.success(page, MessageConverter.INSTANT::entityToVO));
  }

  /**
   * 按聚合组和接收人强制刷新聚合批次。
   *
   * @param group 聚合组标识
   * @param receiver 接收人标识
   * @return 统一响应结果，包含刷新的消息数量
   */
  @Operation(summary = "按聚合组+接收人强制刷新")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_AGGREGATE_REFRESH)
  @Idempotent(key = "ydsz:message:aggregate:flushByGroup", ttlSeconds = 5)
  @Audit(
      module = "聚合批次",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'flushByGroup'")
  @RateLimit(resource = "message.aggregate.flushByGroup", threshold = 50)
  @PostMapping("/flush")
  public YdszResponse<Integer> flushByGroup(
      @RequestParam String group, @RequestParam String receiver) {
    return YdszResponse.success(aggregateService.flushByGroup(group, receiver));
  }

  /**
   * 刷新全部到期聚合批次。
   *
   * @return 统一响应结果，包含刷新的消息数量
   */
  @Operation(summary = "刷新到期批次")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_AGGREGATE_REFRESH)
  @Idempotent(key = "ydsz:message:aggregate:flushDue", ttlSeconds = 5)
  @Audit(
      module = "聚合批次",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'flushDue'")
  @RateLimit(resource = "message.aggregate.flushDue", threshold = 50)
  @PostMapping("/flushDue")
  public YdszResponse<Integer> flushDue() {
    return YdszResponse.success(aggregateService.flushDue());
  }
}
