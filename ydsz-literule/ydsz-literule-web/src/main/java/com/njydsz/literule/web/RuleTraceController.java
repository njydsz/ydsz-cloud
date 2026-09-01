package com.njydsz.literule.web;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.lock.annotation.IdempotentExempt;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.literule.domain.enums.LiteruleExceptionCode;
import com.njydsz.literule.domain.enums.RuleSeverity;
import com.njydsz.literule.domain.vo.RuleExecutionTraceVO;
import com.njydsz.literule.domain.vo.RuleResultVO;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.server.config.RuleTraceQueryService;

/**
 * 规则执行追踪 Controller
 *
 * <p>业务背景：规则引擎每次评估都会记录执行链路（trace），包含事实快照、 触发结果、严重度等信息。通过历史 trace 可实现规则变更后的回放对比、
 * 变更影响分析、回归验证等能力，是规则治理的核心数据源。
 *
 * <p>核心能力：
 *
 * <ul>
 *   <li>按 traceId / ruleCode / 时间范围查询执行链路
 *   <li>历史回放：用当前规则集重新评估历史事实快照，对比触发差异
 *   <li>批量回放：按时间范围批量回放，统计一致/差异
 *   <li>变更影响预览：用新表达式评估历史 trace，预览影响范围
 * </ul>
 *
 * <p>从 {@link RuleAdminController} 拆分而来，与原文件共享基路径 {@code /ruleEngine/rules}，所有端点 URL 保持不变。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/literule/rules")
@RequiredArgsConstructor
@Validated
@Tag(name = "规则执行追踪", description = "执行链路查询、历史回放与变更影响分析")
public class RuleTraceController {

    /** 变更影响分析查询 trace 记录上限（最多 5000 条） */
  private static final int MAX_TRACE_LIMIT = 5000;

  /** 规则执行轨迹查询服务（P1-12 收口：web 不直接依赖 domain Repository） */
  private final RuleTraceQueryService ruleTraceQueryService;

  /** 规则管理服务 */
  private final RuleAdminService ruleAdminService;

  /** 按 traceId 查询执行链路
   * @param traceId 追踪记录唯一标识
   * @return 执行链路列表
   */
  @GetMapping("/traces/{traceId}")
  public YdszResponse<List<RuleExecutionTraceVO>> getTrace(@PathVariable String traceId) {
    return YdszResponse.success(ruleTraceQueryService.findByTraceId(traceId));
  }

  /** 按规则编码查询最近链路
   * @param ruleCode 规则唯一编码
   * @param limit 返回条数上限（默认 20，最大 100）
   * @return 执行链路列表
   */
  @GetMapping("/traces/rule/{ruleCode}")
  public YdszResponse<List<RuleExecutionTraceVO>> getTracesByRule(
      @PathVariable String ruleCode,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return YdszResponse.success(ruleTraceQueryService.findByRuleCode(ruleCode, limit));
  }

  /**
   * 执行回放：基于 traceId 重放历史执行链路
   *
   * <p>从历史 trace 记录中读取 factsSnapshot，用当前规则集重新评估， 对比历史结果与当前结果，展示规则变更后的差异。
   *
   * @param traceId 追踪 ID
   * @return 回放结果（含历史快照 + 当前评估 + 差异分析）
   */
  @Idempotent(key = "ruleAdmin:replayTrace", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'postmapping'")
  @RateLimit(resource = "literule.rule_trace.replayTrace", threshold = 50)
  @PostMapping("/traces/{traceId}/replay")
  public YdszResponse<Map<String, Object>> replayTrace(@PathVariable String traceId) {
    List<RuleExecutionTraceVO> traces = ruleTraceQueryService.findByTraceId(traceId);

    if (traces.isEmpty()) {
      return YdszResponse.error(
          LiteruleExceptionCode.RULE_NOT_FOUND, "未找到 traceId=" + traceId + " 的执行记录");
    }

    // 取第一条 trace 的 factsSnapshot 作为回放输入
    Map<String, Object> facts = traces.get(0).getFactsSnapshot();
    if (facts == null || facts.isEmpty()) {
      return YdszResponse.error(
          LiteruleExceptionCode.RULE_NOT_FOUND, "traceId=" + traceId + " 的事实快照为空，无法回放");
    }

    // 用当前规则集重新评估
    List<RuleResultVO> currentResults = ruleAdminService.dryRun(null, facts);

    // 构建历史触发规则编码集合
    Set<String> historicalTriggered =
        traces.stream()
            .filter(t -> Boolean.TRUE.equals(t.getTriggered()))
            .map(RuleExecutionTraceVO::getRuleCode)
            .collect(Collectors.toSet());

    // 构建当前触发规则编码集合
    Set<String> currentTriggered =
        currentResults.stream().map(RuleResultVO::getRuleCode).collect(Collectors.toSet());

    // 差异分析
    Set<String> added = new LinkedHashSet<>(currentTriggered);
    added.removeAll(historicalTriggered);

    Set<String> removed = new LinkedHashSet<>(historicalTriggered);
    removed.removeAll(currentTriggered);

    Set<String> unchanged = new LinkedHashSet<>(currentTriggered);
    unchanged.retainAll(historicalTriggered);

    Map<String, Object> replay = new LinkedHashMap<>();
    replay.put("traceId", traceId);
    replay.put("factsSnapshot", facts);
    replay.put("historicalTraces", traces);
    replay.put("currentResults", currentResults);
    replay.put(
        "diff",
        Map.of(
            "added", added,
            "removed", removed,
            "unchanged", unchanged,
            "summary",
                String.format(
                    "新增触发 %d 条，移除触发 %d 条，保持不变 %d 条",
                    added.size(), removed.size(), unchanged.size())));

    return YdszResponse.success(replay);
  }

  /**
   * P2-1 批量历史数据回放
   *
   * <p>按时间范围查询历史 trace，用当前规则集重新评估每条 trace 的事实快照， 对比历史结果与当前结果，生成差异报告。
   *
   * <p>差异类型：
   *
   * <ul>
   *   <li>consistent：历史与当前触发状态一致
   *   <li>diff：历史与当前触发状态不一致（含触发→未触发、未触发→触发、严重度变化）
   * </ul>
   *
   * <p>请求体示例：
   *
   * <pre>
   * {
   *   "startTime": "2026-07-01T00:00:00",
   *   "endTime": "2026-07-07T00:00:00",
   *   "ruleCode": "EVM_RED_ALERT",  // 可选，为空表示全部规则
   *   "limit": 100                   // 默认 100，最大 1000
   * }
   * </pre>
   *
   * @param request 请求体（startTime / endTime / ruleCode / limit）
   * @return 批量回放差异报告
   */
  @Idempotent(key = "ruleAdmin:batchReplayTraces", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'postmapping'")
  @RateLimit(resource = "literule.rule_trace.batchReplayTraces", threshold = 50)
  @PostMapping("/traces/batch-replay")
  public YdszResponse<Map<String, Object>> batchReplayTraces(
      @RequestBody Map<String, Object> request) {
    // 解析请求参数
    String startTimeStr = (String) request.get("startTime");
    String endTimeStr = (String) request.get("endTime");
    String ruleCode = (String) request.get("ruleCode");
    int limit = request.containsKey("limit") ? ((Number) request.get("limit")).intValue() : 100;
    if (limit <= 0 || limit > 1000) {
      limit = 100;
    }

    if (startTimeStr == null || endTimeStr == null) {
      return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, "startTime 和 endTime 不能为空");
    }

    LocalDateTime startTime = LocalDateTime.parse(startTimeStr);
    LocalDateTime endTime = LocalDateTime.parse(endTimeStr);

    // 按时间范围查询历史 trace（可选按 ruleCode 过滤）
    List<RuleExecutionTraceVO> traces =
        ruleTraceQueryService.findRecentByRuleCode(ruleCode, limit);

    // 逐条回放：用当前规则集重新评估
    List<Map<String, Object>> diffs = new ArrayList<>();
    int consistentCount = 0;
    int diffCount = 0;

    for (RuleExecutionTraceVO trace : traces) {
      Map<String, Object> facts = trace.getFactsSnapshot();
      if (facts == null || facts.isEmpty()) {
        continue;
      }

      // 用当前规则集对单条规则重新评估
      List<RuleResultVO> currentResults = ruleAdminService.dryRun(trace.getRuleCode(), facts);
      RuleResultVO currentResult =
          currentResults.stream()
              .filter(r -> trace.getRuleCode().equals(r.getRuleCode()))
              .findFirst()
              .orElse(null);

      boolean historicalTriggered = Boolean.TRUE.equals(trace.getTriggered());
      boolean currentTriggered = currentResult != null && currentResult.isTriggered();
      String historicalSeverity = trace.getSeverity();
      String currentSeverity =
          currentResult != null && currentResult.getSeverity() != null
              ? currentResult.getSeverity().name()
              : null;

      // 严重度归一化（null 视为一致）
      boolean severityConsistent = severityEquals(historicalSeverity, currentSeverity);

      if (historicalTriggered == currentTriggered && severityConsistent) {
        consistentCount++;
      } else {
        diffCount++;
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("traceId", trace.getTraceId());
        diff.put("ruleCode", trace.getRuleCode());
        diff.put("historicalTriggered", historicalTriggered);
        diff.put("currentTriggered", currentTriggered);
        diff.put("historicalSeverity", historicalSeverity);
        diff.put("currentSeverity", currentSeverity);
        diff.put(
            "diffType", classifyDiff(historicalTriggered, currentTriggered, severityConsistent));
        diffs.add(diff);
      }
    }

    Map<String, Object> report = new LinkedHashMap<>();
    report.put("totalReplayed", traces.size());
    report.put("consistentCount", consistentCount);
    report.put("diffCount", diffCount);
    report.put("diffs", diffs);
    report.put(
        "summary",
        String.format("共回放 %d 条，一致 %d 条，差异 %d 条", traces.size(), consistentCount, diffCount));

    return YdszResponse.success(report);
  }

  /**
   * P2-2 规则变更影响分析
   *
   * <p>接收规则定义变更（新条件表达式），从历史 trace 中查询该规则最近 N 条记录， 用新表达式重新评估每条 trace 的事实快照，预览变更后的影响范围。
   *
   * <p>请求体示例：
   *
   * <pre>
   * {
   *   "conditionExpression": "evmRedCount >= 5",
   *   "severityExpression": "evmRedCount >= 10 ? 'RED' : 'YELLOW'",
   *   "defaultSeverity": "YELLOW",
   *   "limit": 1000
   * }
   * </pre>
   *
   * <p>影响类型：
   *
   * <ul>
   *   <li>added：历史未触发，新表达式触发（新增触发）
   *   <li>removed：历史触发，新表达式未触发（减少触发）
   *   <li>severityChanged：触发状态不变，但严重度变化
   *   <li>unchanged：触发状态和严重度均不变
   * </ul>
   *
   * @param ruleCode 规则编码
   * @param request 请求体（conditionExpression / severityExpression / defaultSeverity / limit）
   * @return 影响分析报告
   */
  @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'postmapping'")
  @RateLimit(resource = "literule.rule_trace.impactPreview", threshold = 50)
  @PostMapping("/{ruleCode}/impact-preview")
  public YdszResponse<Map<String, Object>> impactPreview(
      @PathVariable String ruleCode, @RequestBody Map<String, Object> request) {
    String conditionExpression = (String) request.get("conditionExpression");
    String severityExpression = (String) request.get("severityExpression");
    String defaultSeverityStr = (String) request.get("defaultSeverity");
    int limit = request.containsKey("limit") ? ((Number) request.get("limit")).intValue() : 1000;
    if (limit <= 0 || limit > MAX_TRACE_LIMIT) {
      limit = 1000;
    }

    if (conditionExpression == null || conditionExpression.isBlank()) {
      return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, "conditionExpression 不能为空");
    }

    // 解析默认严重度
    RuleSeverity defaultSeverity = null;
    if (defaultSeverityStr != null && !defaultSeverityStr.isBlank()) {
      try {
        defaultSeverity = RuleSeverity.valueOf(defaultSeverityStr);
      } catch (IllegalArgumentException e) {
        return YdszResponse.error(
            YdszResultCode.VALIDATION_FAILED,
            "非法的 defaultSeverity: " + defaultSeverityStr + "，合法值: INFO / YELLOW / RED");
      }
    }

    // 查询该规则最近 N 条 trace
    List<RuleExecutionTraceVO> traces =
        ruleTraceQueryService.findRecentByRuleCode(ruleCode, limit);

    // 逐条用新表达式重新评估
    List<Map<String, Object>> affectedTraces = new ArrayList<>();
    int historicalTriggeredCount = 0;
    int newTriggeredCount = 0;
    int addedTriggeredCount = 0;
    int removedTriggeredCount = 0;

    for (RuleExecutionTraceVO trace : traces) {
      Map<String, Object> facts = trace.getFactsSnapshot();
      if (facts == null || facts.isEmpty()) {
        continue;
      }

      // 用新表达式评估
      RuleResultVO newResult =
          ruleAdminService.evaluateWithExpression(
              ruleCode, conditionExpression, severityExpression, defaultSeverity, facts);

      boolean historicalTriggered = Boolean.TRUE.equals(trace.getTriggered());
      boolean newTriggered = newResult.isTriggered();
      String historicalSeverity = trace.getSeverity();
      String newSeverity = newResult.getSeverity() != null ? newResult.getSeverity().name() : null;

      if (historicalTriggered) {
        historicalTriggeredCount++;
      }
      if (newTriggered) {
        newTriggeredCount++;
      }

      // 分类影响
      String impactType;
      if (!historicalTriggered && newTriggered) {
        addedTriggeredCount++;
        impactType = "added";
      } else if (historicalTriggered && !newTriggered) {
        removedTriggeredCount++;
        impactType = "removed";
      } else if (historicalTriggered == newTriggered
          && !severityEquals(historicalSeverity, newSeverity)) {
        impactType = "severityChanged";
      } else {
        impactType = "unchanged";
      }

      // 仅记录受影响的 trace（非 unchanged）
      if (!"unchanged".equals(impactType)) {
        Map<String, Object> affected = new LinkedHashMap<>();
        affected.put("traceId", trace.getTraceId());
        affected.put("historicalTriggered", historicalTriggered);
        affected.put("newTriggered", newTriggered);
        affected.put("historicalSeverity", historicalSeverity);
        affected.put("newSeverity", newSeverity);
        affected.put("impactType", impactType);
        affected.put("traceTime", trace.getId());
        affectedTraces.add(affected);
      }
    }

    Map<String, Object> report = new LinkedHashMap<>();
    report.put("ruleCode", ruleCode);
    report.put("conditionExpression", conditionExpression);
    report.put("totalTraces", traces.size());
    report.put("historicalTriggeredCount", historicalTriggeredCount);
    report.put("newTriggeredCount", newTriggeredCount);
    report.put("addedTriggeredCount", addedTriggeredCount);
    report.put("removedTriggeredCount", removedTriggeredCount);
    report.put("affectedTraces", affectedTraces);
    report.put(
        "summary",
        String.format(
            "共分析 %d 条 trace，历史触发 %d 条，新表达式触发 %d 条（新增 %d，减少 %d）",
            traces.size(),
            historicalTriggeredCount,
            newTriggeredCount,
            addedTriggeredCount,
            removedTriggeredCount));

    return YdszResponse.success(report);
  }

  /**
   * 比较两个严重度字符串是否一致（null 与 null 视为一致）
   *
   * @param s1 严重度 1
   * @param s2 严重度 2
   * @return true=一致
   */
  private boolean severityEquals(String s1, String s2) {
    if (s1 == null && s2 == null) {
      return true;
    }
    if (s1 == null || s2 == null) {
      return false;
    }
    return s1.equalsIgnoreCase(s2);
  }

  /**
   * 分类差异类型
   *
   * @param historicalTriggered 历史是否触发
   * @param currentTriggered 当前是否触发
   * @param severityConsistent 严重度是否一致
   * @return 差异类型：triggered_to_not / not_to_triggered / severity_changed / consistent
   */
  private String classifyDiff(
      boolean historicalTriggered, boolean currentTriggered, boolean severityConsistent) {
    if (historicalTriggered && !currentTriggered) {
      return "triggered_to_not";
    }
    if (!historicalTriggered && currentTriggered) {
      return "not_to_triggered";
    }
    if (!severityConsistent) {
      return "severity_changed";
    }
    return "consistent";
  }

  /**
   * 查询最近执行链路（按时间倒序）
   *
   * @param limit 返回条数（默认 50）
   * @return 最近的执行链路列表
   */
  @GetMapping("/traces")
  public YdszResponse<List<RuleExecutionTraceVO>> listRecentTraces(
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
    return YdszResponse.success(ruleTraceQueryService.findRecent(limit));
  }
}
