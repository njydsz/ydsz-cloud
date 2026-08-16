package com.njydsz.literule.web.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.literule.domain.vo.AuditLogEntryVO;
import com.njydsz.literule.server.audit.RuleAuditLogService;
import com.njydsz.literule.server.audit.RuleAuditLogService.AuditAction;

/**
 * 规则审计日志查询接口（P3-5）
 *
 * <p>暴露 {@link RuleAuditLogService} 的多维查询能力，支持按规则编码、操作人、 操作类型、时间范围查询审计日志，以及查询最近审计记录。
 *
 * <h3>接口列表</h3>
 *
 * <ul>
 *   <li>{@code GET /ruleEngine/audit/recent} - 查询最近审计日志
 *   <li>{@code GET /ruleEngine/audit/byRule/{ruleCode}} - 按规则编码查询
 *   <li>{@code GET /ruleEngine/audit/byOperator} - 按操作人查询
 *   <li>{@code GET /ruleEngine/audit/byAction} - 按操作类型查询
 *   <li>{@code GET /ruleEngine/audit/byTimeRange} - 按时间范围查询
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/rule-engine/audit")
@RequiredArgsConstructor
@ConditionalOnBean(RuleAuditLogService.class)
@Tag(name = "规则审计日志", description = "P3-5 规则操作审计日志查询 API")
public class RuleAuditLogController {

  private final RuleAuditLogService auditLogService;

  /**
   * 查询最近的审计日志
   *
   * @param limit 返回条数（默认 50，最大 200）
   * @return 审计日志列表（按时间倒序）
   */
  @GetMapping("/recent")
  @Operation(summary = "查询最近审计日志", description = "返回最近 N 条审计日志，按时间倒序排列")
  public BaseResponse<List<AuditLogEntryVO>> recent(
      @RequestParam(value = "limit", defaultValue = "50") int limit) {
    if (limit <= 0 || limit > 200) {
      limit = 50;
    }
    return BaseResponse.success(
        auditLogService.queryRecent(limit).stream().map(this::toAuditLogVO).toList());
  }

  /**
   * 按规则编码查询审计日志
   *
   * @param ruleCode 规则编码
   * @param limit 返回条数（默认 50，最大 200）
   * @return 审计日志列表
   */
  @GetMapping("/by-rule/{ruleCode}")
  @Operation(summary = "按规则编码查询审计日志", description = "返回指定规则的全生命周期操作记录")
  public BaseResponse<List<AuditLogEntryVO>> byRuleCode(
      @PathVariable String ruleCode,
      @RequestParam(value = "limit", defaultValue = "50") int limit) {
    if (limit <= 0 || limit > 200) {
      limit = 50;
    }
    return BaseResponse.success(
        auditLogService.queryByRuleCode(ruleCode, limit).stream().map(this::toAuditLogVO).toList());
  }

  /**
   * 按操作人查询审计日志
   *
   * @param operator 操作人（工号/SSO 用户名）
   * @param limit 返回条数（默认 50，最大 200）
   * @return 审计日志列表
   */
  @GetMapping("/by-operator")
  @Operation(summary = "按操作人查询审计日志", description = "返回指定操作人的审计日志")
  public BaseResponse<List<AuditLogEntryVO>> byOperator(
      @RequestParam("operator") String operator,
      @RequestParam(value = "limit", defaultValue = "50") int limit) {
    if (limit <= 0 || limit > 200) {
      limit = 50;
    }
    return BaseResponse.success(
        auditLogService.queryByOperator(operator, limit).stream().map(this::toAuditLogVO).toList());
  }

  /**
   * 按操作类型查询审计日志
   *
   * @param action 操作类型（CREATE / UPDATE / TOGGLE / ROLLBACK / APPROVE / REJECT / IMPORT / EXPORT /
   *     DELETE）
   * @param limit 返回条数（默认 50，最大 200）
   * @return 审计日志列表
   */
  @GetMapping("/by-action")
  @Operation(summary = "按操作类型查询审计日志", description = "返回指定操作类型的审计日志")
  public BaseResponse<List<AuditLogEntryVO>> byAction(
      @RequestParam("action") String action,
      @RequestParam(value = "limit", defaultValue = "50") int limit) {
    if (limit <= 0 || limit > 200) {
      limit = 50;
    }
    try {
      AuditAction auditAction = AuditAction.valueOf(action.toUpperCase());
      return BaseResponse.success(
          auditLogService.queryByAction(auditAction, limit).stream()
              .map(this::toAuditLogVO)
              .toList());
    } catch (IllegalArgumentException e) {
      return BaseResponse.error(
          BaseResultCode.VALIDATION_FAILED,
          "非法的操作类型: "
              + action
              + "，合法值: CREATE / UPDATE / TOGGLE / STATUS_CHANGE / ROLLBACK / APPROVE / REJECT / IMPORT / EXPORT / DELETE / DRY_RUN / STRESS_TEST / REPLAY");
    }
  }

  /**
   * 按时间范围查询审计日志
   *
   * @param startTime 开始时间（ISO 格式，如 2026-07-01T00:00:00）
   * @param endTime 结束时间（ISO 格式）
   * @param limit 返回条数（默认 50，最大 500）
   * @return 审计日志列表
   */
  @GetMapping("/by-time-range")
  @Operation(summary = "按时间范围查询审计日志", description = "返回指定时间范围内的审计日志")
  public BaseResponse<List<AuditLogEntryVO>> byTimeRange(
      @RequestParam("startTime") String startTime,
      @RequestParam("endTime") String endTime,
      @RequestParam(value = "limit", defaultValue = "50") int limit) {
    if (limit <= 0 || limit > 500) {
      limit = 50;
    }
    try {
      LocalDateTime start = LocalDateTime.parse(startTime);
      LocalDateTime end = LocalDateTime.parse(endTime);
      return BaseResponse.success(
          auditLogService.queryByTimeRange(start, end, limit).stream()
              .map(this::toAuditLogVO)
              .toList());
    } catch (Exception e) {
      return BaseResponse.error(
          BaseResultCode.VALIDATION_FAILED,
          "时间格式错误，请使用 ISO 格式（如 2026-07-01T00:00:00）: " + e.getMessage());
    }
  }

  /** AuditLogEntry → AuditLogEntryVO 转换 */
  private AuditLogEntryVO toAuditLogVO(RuleAuditLogService.AuditLogEntry e) {
    AuditLogEntryVO vo = new AuditLogEntryVO();
    vo.setId(e.getId());
    vo.setRuleCode(e.getRuleCode());
    vo.setRuleName(e.getRuleName());
    vo.setAction(e.getAction() != null ? e.getAction().name() : null);
    vo.setOperator(e.getOperator());
    vo.setSource(e.getSource());
    vo.setChangeDesc(e.getChangeDesc());
    vo.setBeforeSnapshot(e.getBeforeSnapshot());
    vo.setAfterSnapshot(e.getAfterSnapshot());
    if (e.getFieldDiffs() != null) {
      Map<String, Object> diffs = new LinkedHashMap<>();
      e.getFieldDiffs().forEach(diffs::put);
      vo.setFieldDiffs(diffs);
    }
    vo.setResult(e.getResult() != null ? e.getResult().name() : null);
    vo.setErrorMessage(e.getErrorMessage());
    vo.setCreatedAt(e.getCreatedAt());
    return vo;
  }
}
