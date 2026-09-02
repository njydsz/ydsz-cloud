package com.njydsz.cronjob.web.controller.audit;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.domain.vo.AuditLogVO;
import com.njydsz.cronjob.server.service.audit.AuditLogService;

/**
 * 操作审计视图 Controller（P1-14 操作审计视图）。
 *
 * <p>提供 cronjob 模块操作审计日志的查询接口，支持分页、时间范围、操作类型过滤。
 *
 * <h3>数据来源</h3>
 *
 * <p>数据来自 {@code ydsz_job_audit_log} 表（由 common-audit 模块写入），本接口仅读取
 * {@code module = 'cronjob'} 的记录，展示任务调度相关的操作轨迹：创建、更新、暂停、
 * 恢复、触发、删除等。
 *
 * <h3>权限</h3>
 *
 * <p>需要 {@code CRONJOB_AUDIT_VIEW} 权限，通常仅管理员可查看。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Tag(name = "操作审计", description = "cronjob 操作审计日志分页查询")
@Slf4j
@RestController
@RequestMapping("/api/v1/cronjob/audit")
@RequiredArgsConstructor
@Validated
public class AuditLogController {

  /** 审计日志服务 */
  private final AuditLogService auditLogService;

  /**
   * 分页查询 cronjob 模块的操作审计日志。
   *
   * <p>支持按操作行为编码、操作人、时间范围过滤，按操作时间降序排列。
   *
   * @param pageNum 页码（默认 1）
   * @param size 每页条数（默认 20，最大 100）
   * @param action 操作行为编码（可选，对应 AuditAction 枚举值）
   * @param operatorName 操作人姓名（可选）
   * @param startTime 开始时间（可选，ISO-8601 格式如 2025-01-01T00:00:00）
   * @param endTime 结束时间（可选，ISO-8601 格式如 2025-01-31T23:59:59）
   * @return 分页审计日志列表
   */
  @Operation(summary = "分页查询操作审计日志")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_AUDIT_VIEW)
  @GetMapping("/page")
  public YdszResponse<PageResponse<List<AuditLogVO>>> page(
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Integer action,
      @RequestParam(required = false) String operatorName,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime startTime,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime endTime) {
    return YdszResponse.success(
        auditLogService.page(pageNum, size, action, operatorName, startTime, endTime));
  }
}
