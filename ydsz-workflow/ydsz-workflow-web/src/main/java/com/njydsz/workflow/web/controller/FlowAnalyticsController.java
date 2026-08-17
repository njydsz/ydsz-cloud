package com.njydsz.workflow.web.controller.analytics;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.workflow.server.service.FlowAnalyticsService;
import com.njydsz.workflow.server.service.FlowHistoryArchiveService;
import com.njydsz.workflow.server.service.FlowI18nService;

/**
 * 审批数据分析 Controller（P2-2）
 *
 * <p>提供工作流「审批分析仪表盘」HTTP API，覆盖总览、效率排行、节点耗时、趋势等 多维度数据分析能力，对标钉钉/飞书审批中心的"数据分析"模块。
 *
 * <p><b>接口路径：</b>{@code /api/v1/workflow/analytics/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li>{@code GET /overview} — 审批总览仪表盘（核心 KPI）
 *   <li>{@code GET /approverEfficiency} — 办理人效率排行
 *   <li>{@code GET /flowEfficiency} — 流程效率对比
 *   <li>{@code GET /nodeDuration} — 节点耗时分析
 *   <li>{@code GET /approvalTrend} — 审批趋势分析（时间序列）
 * </ul>
 *
 * <p><b>多租户隔离：</b>所有查询按 {@link TenantContextHolder#getTenantId} 隔离。
 *
 * <p><b>性能优化：</b>
 *
 * <ul>
 *   <li>OLAP 聚合走 {@code ydsz_flow_his_task} / {@code ydsz_flow_instance} 复合索引
 *   <li>趋势 / 排行类查询通过 GROUP BY 单次聚合，O(1) 返回
 *   <li>聚合结果走 Redis 缓存（{@code ydsz:workflow:analytics:*}），TTL 5min
 * </ul>
 *
 * <p><b>设计原则：</b>Controller 仅做参数透传与时间范围解析； 指标计算、聚合 SQL 编排、缓存管理下沉到 {@link FlowAnalyticsService}。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowAnalyticsService 审批数据分析服务
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workflow/analytics")
@RequiredArgsConstructor
@Tag(name = "审批数据分析", description = "审批效率/驳回率/办理人排行等分析仪表盘与历史归档")
public class FlowAnalyticsController {

  /** 审批数据分析服务，提供效率排行、趋势分析等统计能力 */
  private final FlowAnalyticsService analyticsService;

  /** 流程历史归档服务，负责数据归档、冷数据清理与配置查询 */
  private final FlowHistoryArchiveService archiveService;

  /** 国际化服务，负责多语言枚举描述的查询、缓存与 fallback */
  private final FlowI18nService i18nService;

  /**
   * 审批总览仪表盘
   *
   * <p>聚合当前租户下核心 KPI：发起数 / 完成数 / 通过率 / 平均耗时 / 超期率 / 在途实例数。
   *
   * <p>供管理端"工作流首页"或"分析仪表盘"卡片展示。
   *
   * <p>时间范围参数均可选，未传则默认全量。
   *
   * @param startTime 查询起始时间（ISO 8601 格式，可选）
   * @param endTime 查询截止时间（ISO 8601 格式，可选）
   * @return 总览统计数据（含 KPI 字段）
   */
  @GetMapping("/overview")
  @Operation(summary = "审批总览仪表盘")
  public BaseResponse<Object> overview(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime startTime,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime endTime) {
    return BaseResponse.success(
        analyticsService.overview(startTime, endTime, TenantContextHolder.getTenantId()));
  }

  /**
   * 办理人效率排行
   *
   * <p>按"人均完成数 / 平均处理耗时"双维度排序，输出 Top N 审批人。
   *
   * <p>供审批人效率看板使用，可识别高效 / 低效审批人。
   *
   * @param startTime 查询起始时间（可选）
   * @param endTime 查询截止时间（可选）
   * @param limit 返回条数上限，默认 20
   * @return 办理人效率排行列表（含 userId / userName / completedCount / avgDurationMs 等字段）
   */
  @GetMapping("/approverEfficiency")
  @Operation(summary = "办理人效率排行")
  public BaseResponse<Object> approverEfficiency(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime startTime,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime endTime,
      @RequestParam(defaultValue = "20") int limit) {
    return BaseResponse.success(
        analyticsService.approverEfficiency(
            startTime, endTime, TenantContextHolder.getTenantId(), limit));
  }

  /**
   * 流程效率对比
   *
   * <p>横向对比不同流程类型的效率指标（按流程编码聚合），用于识别哪些流程效率高、哪些是瓶颈流程。
   *
   * @param startTime 查询起始时间（可选）
   * @param endTime 查询截止时间（可选）
   * @return 各流程效率对比数据
   */
  @GetMapping("/flowEfficiency")
  @Operation(summary = "流程效率对比")
  public BaseResponse<Object> flowEfficiency(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime startTime,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime endTime) {
    return BaseResponse.success(
        analyticsService.flowEfficiencyComparison(
            startTime, endTime, TenantContextHolder.getTenantId()));
  }

  /**
   * 节点耗时分析
   *
   * <p>按节点维度分析平均 / 最大 / P50 / P90 耗时，用于识别流程瓶颈节点。
   *
   * <p>典型用途：流程优化前评估，找最耗时的审批环节。
   *
   * @param flowCode 流程编码
   * @return 各节点耗时统计数据
   */
  @GetMapping("/nodeDuration")
  @Operation(summary = "节点耗时分析")
  public BaseResponse<Object> nodeDuration(@RequestParam String flowCode) {
    return BaseResponse.success(
        analyticsService.nodeDurationStats(flowCode, TenantContextHolder.getTenantId()));
  }

  /**
   * 审批趋势分析
   *
   * <p>按时间序列（天 / 周 / 月）统计审批发起数 / 完成数 / 通过数 / 驳回数，绘制趋势图。
   *
   * @param startTime 查询起始时间（可选）
   * @param endTime 查询截止时间（可选）
   * @param granularity 统计粒度，默认 DAY（可选 HOUR / DAY / WEEK / MONTH）
   * @return 审批趋势时间序列数据
   */
  @GetMapping("/approvalTrend")
  @Operation(summary = "审批趋势分析")
  public BaseResponse<Object> approvalTrend(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime startTime,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime endTime,
      @RequestParam(defaultValue = "DAY") String granularity) {
    return BaseResponse.success(
        analyticsService.approvalTrend(
            startTime, endTime, TenantContextHolder.getTenantId(), granularity));
  }

  // ==================== 流程历史数据归档管理 ====================

  /**
   * 查询当前归档配置
   *
   * @return 配置项 Map
   */
  @Operation(summary = "查询归档配置")
  @GetMapping("/history/config")
  public BaseResponse<Map<String, Object>> getArchiveConfig() {
    return BaseResponse.success(archiveService.getArchiveConfig());
  }

  /**
   * 手动触发归档
   *
   * <p>参数可选，未传则使用 {@code application.yml} 配置的默认值。 适用于临时归档更早的数据（如手动归档 90 天前的数据）。
   *
   * @param retentionDays 归档阈值天数（可选）
   * @param batchSize 单次批量大小（可选）
   * @param maxProcessMs 单次最大耗时毫秒（可选）
   * @return 执行结果摘要
   */
  @Audit(
      module = "历史归档",
      type = AuditType.OPERATION,
      action = AuditAction.BACKUP,
      content = "'archive'")
  @Operation(summary = "手动触发归档")
  @Idempotent(key = "ydsz:workflow:FlowAnalyticsController:archive:lock", ttlSeconds = 5)
  @PostMapping("/history/archive")
  public BaseResponse<Map<String, Object>> archive(
      @RequestParam(required = false) @Min(1) Integer retentionDays,
      @RequestParam(required = false) @Min(1) @Max(1000) Integer batchSize,
      @RequestParam(required = false) Long maxProcessMs) {
    log.info(
        "[FlowAnalyticsController] 手动触发归档 retentionDays={} batchSize={} maxProcessMs={}",
        retentionDays,
        batchSize,
        maxProcessMs);
    return BaseResponse.success(archiveService.archive(retentionDays, batchSize, maxProcessMs));
  }

  /**
   * 手动触发清理（purge）
   *
   * <p>清理归档表中超过阈值的冷数据，回收存储空间。 即使配置 {@code purge-enabled=false}，本接口仍可强制执行（参数优先于配置）。
   *
   * @param purgeDays 清理阈值天数（可选，默认使用配置值）
   * @return 执行结果摘要
   */
  @Audit(
      module = "历史归档",
      type = AuditType.OPERATION,
      action = AuditAction.CLEAN,
      content = "'purge'")
  @Operation(summary = "手动触发清理（purge）")
  @Idempotent(key = "ydsz:workflow:FlowAnalyticsController:purge:lock", ttlSeconds = 5)
  @PostMapping("/history/purge")
  public BaseResponse<Map<String, Object>> purge(
      @RequestParam(required = false) Integer purgeDays) {
    log.info("[FlowAnalyticsController] 手动触发清理 purgeDays={}", purgeDays);
    return BaseResponse.success(archiveService.purge(purgeDays));
  }

  // ==================== 工作流国际化 (i18n) ====================

  /**
   * 获取指定枚举类的全部描述
   *
   * <p>返回的 Map 列表中每条形如 {@code {name: "RUNNING", label: "运行中"}}， 前端可直接用于下拉框 / 单选 / 状态筛选组件渲染。
   *
   * @param enumType 枚举类型（FlowTaskStatus / FlowInstanceStatus / FlowNodeType 等）
   * @param locale 语言（zh_CN/en_US），为空默认 zh_CN
   * @return 枚举描述列表（含 name + label 字段）
   */
  @GetMapping("/i18n/enum/{enumType}")
  @Operation(summary = "获取枚举类型的全部描述")
  public BaseResponse<List<Map<String, String>>> enumDescriptions(
      @PathVariable String enumType, @RequestParam(required = false) String locale) {
    return BaseResponse.success(i18nService.getEnumDescriptions(enumType, locale));
  }

  /**
   * 获取单个枚举值的描述
   *
   * <p>适用于只需要展示某一个枚举值描述的场景（如详情页某字段的状态文案）。
   *
   * @param enumType 枚举类型
   * @param enumName 枚举值名称（如 {@code RUNNING}）
   * @param locale 语言
   * @return 描述文本（无翻译时回退到原值）
   */
  @GetMapping("/i18n/enum/{enumType}/{enumName}")
  @Operation(summary = "获取单个枚举值的描述")
  public BaseResponse<String> enumDescription(
      @PathVariable String enumType,
      @PathVariable String enumName,
      @RequestParam(required = false) String locale) {
    return BaseResponse.success(i18nService.getEnumDescription(enumType, enumName, locale));
  }

  /**
   * 获取所有支持的语言列表
   *
   * <p>前端首次加载时调用，构建语言切换下拉框；返回形如 {@code [{code: "zh_CN", name: "简体中文"}, {code: "en_US", name:
   * "English"}]}。
   *
   * @return 语言列表（含 code 与 name 字段）
   */
  @GetMapping("/i18n/locales")
  @Operation(summary = "获取支持的语言列表")
  public BaseResponse<List<Map<String, String>>> supportedLocales() {
    return BaseResponse.success(i18nService.getSupportedLocales());
  }
}
