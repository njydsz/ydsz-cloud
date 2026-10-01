package com.njydsz.cronjob.web.controller.audit;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.core.AuditQueryService;
import com.njydsz.common.audit.domain.AuditLog;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.PermissionCodes;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.cronjob.domain.query.AuditLogQuery;
import com.njydsz.cronjob.domain.vo.AuditLogVO;

/**
 * 操作审计视图 Controller（P1-14 操作审计视图）。
 *
 * <p>提供 cronjob 模块操作审计日志的查询接口，支持分页、时间范围、操作类型过滤。
 *
 * <h3>数据来源</h3>
 *
 * <p>数据来自 {@code sys_audit_log} 表（由 common-audit 模块写入），通过 {@link AuditQueryService}
 * 查询 {@code module = 'cronjob'} 的记录，展示任务调度相关的操作轨迹：创建、更新、暂停、
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
@ApiVersion("26.09.01")
@RestController
@RequestMapping("/cronjob/audit")
@RequiredArgsConstructor
@Validated
public class AuditLogController {

  private static final String MODULE_CRONJOB = "cronjob";

  /** 审计查询服务（来自 ydsz-common-audit） */
  private final AuditQueryService auditQueryService;

  /**
   * 分页查询 cronjob 模块的操作审计日志。
   *
   * <p>支持按操作行为编码、操作人、时间范围过滤，按操作时间降序排列。
   *
   * @param query 分页查询参数（含 pageNum/pageSize/过滤条件）
   * @return 分页审计日志列表
   */
  @Operation(summary = "分页查询操作审计日志")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_AUDIT_VIEW)
  @GetMapping("/page")
  public YdszResponse<PageResponse<List<AuditLogVO>>> page(@Validated AuditLogQuery query) {
    YdszResponse<List<AuditLog>> response =
        auditQueryService.queryByTimeRange(
            query.getStartTime(), query.getEndTime(), query.getPageNum(), query.getPageSize());

    @SuppressWarnings("unchecked")
    PageResponse<List<AuditLog>> pageResponse = (PageResponse<List<AuditLog>>) response;

    List<AuditLog> records = pageResponse.getData();
    if (records == null || records.isEmpty()) {
      return YdszResponse.success(
          PageResponse.empty((long) query.getPageNum(), (long) query.getPageSize()));
    }

    List<AuditLogVO> voList = records.stream()
        .filter(log -> matchesAction(log, query.getAction()))
        .filter(log -> matchesOperatorName(log, query.getOperatorName()))
        .map(this::toAuditLogVO)
        .collect(Collectors.toList());

    PageResponse<List<AuditLogVO>> result = PageResponse.success(
        pageResponse.getTotal(),
        pageResponse.getPageNum(),
        pageResponse.getPageSize(),
        voList);
    log.info("分页查询 cronjob 审计日志完成，total={}, pageNum={}, pageSize={}, returned={}",
        result.getTotal(), result.getPageNum(), result.getPageSize(), voList.size());
    return YdszResponse.success(result);
  }

  private boolean matchesAction(AuditLog log, Integer action) {
    return action == null || Objects.equals(log.getAction(), action);
  }

  private boolean matchesOperatorName(AuditLog log, String operatorName) {
    if (operatorName == null || operatorName.isEmpty()) {
      return true;
    }
    return log.getOperatorName() != null && log.getOperatorName().equals(operatorName);
  }

  private AuditLogVO toAuditLogVO(AuditLog log) {
    AuditLogVO vo = new AuditLogVO();
    vo.setId(log.getId());
    vo.setAuditType(log.getAuditType());
    vo.setAction(log.getAction());
    vo.setModule(log.getModule());
    vo.setContent(log.getContent());
    vo.setBusinessNo(log.getBusinessNo());
    vo.setOperatorName(log.getOperatorName());
    vo.setOperationTime(log.getOperationTime());
    vo.setIpAddress(log.getIpAddress());
    vo.setCostTime(log.getCostTime());
    vo.setTraceId(log.getTraceId());
    return vo;
  }
}
