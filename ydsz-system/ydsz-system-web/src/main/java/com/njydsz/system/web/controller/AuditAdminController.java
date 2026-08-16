package com.njydsz.system.web.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.njydsz.common.audit.core.AuditQueryService;
import com.njydsz.common.audit.domain.AuditLog;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.web.version.ApiVersion;

/**
 * 审计日志管理 Controller
 *
 * <p>提供 sys_audit_log 表的查询接口，供运营后台/管理控制台查看操作轨迹。 支持按时间范围、操作人、操作行为、操作模块、追踪 ID 等多维度检索。
 *
 * <p>数据来源：调用 {@link AuditQueryService} (ydsz-common-audit 提供的查询能力)， 充分利用已有查询服务，避免重复编写 SQL 查询逻辑。
 *
 * <p><b>接口路径：</b>{@code /api/v1/admin/audit}
 *
 * <p><b>权限要求：</b>建议通过网关或拦截器限制仅管理员角色访问（ADMIN 权限）。 具体鉴权机制由上层安全框架（如 Spring Security / Shiro）负责。
 *
 * <p><b>查询性能：</b>
 *
 * <ul>
 *   <li>接口依赖数据库索引（operation_time、operator_id、action、trace_id）
 *   <li>启用分表时自动路由到对应分表（由 AuditQueryService 内部处理）
 *   <li>建议查询范围不超过 30 个月，全量扫描会触发深度分页保护
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
@ApiVersion("1")
@Tag(name = "审计日志管理", description = "审计日志查询（运营/管理后台）")
public class AuditAdminController {

  private final AuditQueryService auditQueryService;

  /**
   * 按时间范围分页查询审计日志
   *
   * @param startTime 开始时间（格式：yyyy-MM-dd HH:mm:ss）
   * @param endTime 结束时间（格式：yyyy-MM-dd HH:mm:ss）
   * @param page 页码（从 1 开始，默认 1）
   * @param size 每页大小（默认 20，最大 100）
   * @return 分页查询结果
   */
  @GetMapping("/logs")
  @Operation(summary = "按时间范围分页查询审计日志", description = "查询指定时间范围内的审计日志，按操作时间倒序排列")
  public BaseResponse<List<AuditLog>> queryByTimeRange(
      @Parameter(description = "开始时间（yyyy-MM-dd HH:mm:ss）")
          @RequestParam
          @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
          LocalDateTime startTime,
      @Parameter(description = "结束时间（yyyy-MM-dd HH:mm:ss）")
          @RequestParam
          @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
          LocalDateTime endTime,
      @Parameter(description = "页码（默认 1）") @RequestParam(defaultValue = "1") int page,
      @Parameter(description = "每页大小（默认 20，最大 100）") @RequestParam(defaultValue = "20") int size) {
    int normalizedSize = normalizePageSize(size);
    return auditQueryService.queryByTimeRange(startTime, endTime, page, normalizedSize);
  }

  /**
   * 按操作人分页查询审计日志
   *
   * @param operatorId 操作人 ID
   * @param page 页码
   * @param size 每页大小
   * @return 分页查询结果
   */
  @GetMapping("/operator/{operatorId}")
  @Operation(summary = "按操作人分页查询审计日志", description = "查询指定操作人的审计轨迹")
  public BaseResponse<List<AuditLog>> queryByOperator(
      @Parameter(description = "操作人 ID") @PathVariable String operatorId,
      @Parameter(description = "页码（默认 1）") @RequestParam(defaultValue = "1") int page,
      @Parameter(description = "每页大小（默认 20）") @RequestParam(defaultValue = "20") int size) {
    int normalizedSize = normalizePageSize(size);
    return auditQueryService.queryByOperator(operatorId, page, normalizedSize);
  }

  /**
   * 按操作行为分页查询审计日志
   *
   * @param action 操作行为编码（1=新增, 2=修改, 3=删除, 4=查询, 5=导出...）
   * @param page 页码
   * @param size 每页大小
   * @return 分页查询结果
   */
  @GetMapping("/action/{action}")
  @Operation(summary = "按操作行为分页查询审计日志", description = "按操作行为类型查询审计日志（1=新增, 2=修改, 3=删除, 4=查询, 5=导出）")
  public BaseResponse<List<AuditLog>> queryByAction(
      @Parameter(description = "操作行为编码") @PathVariable Integer action,
      @Parameter(description = "页码（默认 1）") @RequestParam(defaultValue = "1") int page,
      @Parameter(description = "每页大小（默认 20）") @RequestParam(defaultValue = "20") int size) {
    int normalizedSize = normalizePageSize(size);
    return auditQueryService.queryByAction(action, page, normalizedSize);
  }

  /**
   * 按链路追踪 ID 查询审计日志
   *
   * <p>通过 traceId 追踪完整请求链路中的所有操作，适用于问题排查场景。
   *
   * @param traceId 链路追踪 ID
   * @return 审计日志列表（按操作时间倒序）
   */
  @GetMapping("/trace/{traceId}")
  @Operation(summary = "按链路追踪ID查询审计日志", description = "通过 traceId 追踪完整请求链路中的所有操作记录")
  public BaseResponse<List<AuditLog>> queryByTraceId(
      @Parameter(description = "链路追踪 ID") @PathVariable String traceId) {
    List<AuditLog> logs = auditQueryService.queryByTraceId(traceId);
    return BaseResponse.success(logs);
  }

  /**
   * 按 ID 查询单条审计日志详情
   *
   * @param id 审计记录 ID（雪花算法生成）
   * @return 审计日志详情
   */
  @GetMapping("/{id}")
  @Operation(summary = "查询单条审计日志详情", description = "根据审计记录 ID 查询完整的审计日志信息")
  public BaseResponse<AuditLog> getById(
      @Parameter(description = "审计记录 ID") @PathVariable String id) {
    AuditLog log = auditQueryService.getById(id);
    if (log == null) {
      return BaseResponse.fail("审计日志不存在");
    }
    return BaseResponse.success(log);
  }

  /**
   * 规范化分页大小参数
   *
   * @param size 原始分页大小
   * @return 规范化后的分页大小（1~100）
   */
  private int normalizePageSize(int size) {
    if (size < 1) {
      return 20;
    }
    if (size > 100) {
      return 100;
    }
    return size;
  }
}
