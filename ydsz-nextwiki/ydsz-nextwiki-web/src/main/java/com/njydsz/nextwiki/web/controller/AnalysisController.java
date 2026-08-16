package com.njydsz.nextwiki.web.controller;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.server.service.AiSummaryApplicationService;
import com.njydsz.nextwiki.server.service.StorageAnalysisApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 存储分析与 AI 摘要 REST API Controller。
 *
 * <p>提供网盘存储统计报表和文档智能摘要两类能力，是网盘"可视化分析 + AI 增强"特性的对外接口：
 *
 * <ul>
 *   <li>{@code GET /analysis/overview} - 获取用户存储概览（总容量/文件数/各类型分布）
 *   <li>{@code GET /analysis/by-type} - 按文件类型统计（image/document/video/...）
 *   <li>{@code GET /analysis/top-large-files} - 大文件 Top-N（用于空间清理）
 *   <li>{@code POST /analysis/summary} - 生成文档 AI 摘要
 * </ul>
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>存储概览：聚合用户总容量、文件数、文件夹数、各类型占比
 *   <li>大文件识别：返回 TopN 大文件，便于用户清理释放空间
 *   <li>AI 摘要：基于 LLM 提取文档关键信息（标题/摘要/关键词/分类）
 *   <li>可视化：返回结构化数据供前端 ECharts/AntV 等图表库渲染
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 *
 * <ul>
 *   <li>AI 摘要接口（CPU/算力消耗大）加 {@link RateLimit} 限流（50 QPS）
 *   <li>所有写操作加 {@link Idempotent} 防重（5s TTL）
 *   <li>所有接口加 {@link AuthApiPermission} 权限码校验（NEXTWIKI_ANALYSIS）
 *   <li>统计查询走预聚合表，避免大表实时 SQL 聚合
 * </ul>
 *
 * <h3>接口路径</h3>
 *
 * <pre>
 *   GET  /api/v1/nextwiki/analysis/overview         - 存储概览
 *   GET  /api/v1/nextwiki/analysis/by-type          - 按类型统计
 *   GET  /api/v1/nextwiki/analysis/top-large-files  - 大文件 Top-N
 *   POST /api/v1/nextwiki/analysis/summary          - AI 文档摘要
 * </pre>
 *
 * <h3>架构位置</h3>
 *
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-nextwiki-web (本 Controller)
 *                                            ↓
 *                                   ydsz-nextwiki-server
 *                                       ├── StorageAnalysisApplicationService (统计)
 *                                       └── AiSummaryApplicationService (AI 摘要)
 *                                            ↓
 *                                   ydsz-nextwiki-infra Mapper
 *                                            ↓
 *                                   ydsz_file_node (聚合查询)
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/analysis")
@RequiredArgsConstructor
@Tag(name = "存储分析与AI摘要", description = "存储统计报表、文档智能摘要（LLM）")
public class AnalysisController {

  /** 存储分析应用服务（封装存储统计 + 大文件识别） */
  private final StorageAnalysisApplicationService storageAnalysisService;

  /** AI 摘要应用服务（封装 LLM 摘要 + 关键词提取 + 分类） */
  private final AiSummaryApplicationService aiSummaryService;

  /**
   * 获取用户存储概览。
   *
   * <p>返回总容量、文件数、文件夹数、共享文件数等汇总指标，供前端"我的网盘"首页展示。
   *
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为 {@link StorageAnalysisApplicationService.StorageOverview}
   */
  @GetMapping("/overview")
  @Operation(summary = "获取存储概览")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_ANALYSIS)
  public BaseResponse<StorageAnalysisApplicationService.StorageOverview> getOverview(
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
    return BaseResponse.success(storageAnalysisService.getUserOverview(userId));
  }

  /**
   * 按文件类型统计。
   *
   * <p>返回各类型（image/document/video/audio/archive/other）的文件数和总容量， 供前端饼图展示。
   *
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为 {@code Map<类型, TypeStats>}
   */
  @GetMapping("/by-type")
  @Operation(summary = "按文件类型统计")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_ANALYSIS)
  public BaseResponse<Map<String, StorageAnalysisApplicationService.TypeStats>> statsByType(
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
    return BaseResponse.success(storageAnalysisService.statsByType(userId));
  }

  /**
   * 获取大文件 Top-N。
   *
   * <p>按文件大小降序返回前 N 个文件，供"大文件清理"功能使用。
   *
   * @param userId 当前用户 ID
   * @param limit 返回数量上限（默认 10，建议不超过 100）
   * @return 统一响应结果，data 为 {@link FileNode} 列表
   */
  @GetMapping("/top-large-files")
  @Operation(summary = "大文件 Top-N")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_ANALYSIS)
  public BaseResponse<List<FileNode>> topLargeFiles(
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId,
      @RequestParam(defaultValue = "10") int limit) {
    return BaseResponse.success(storageAnalysisService.topLargeFiles(userId, limit));
  }

  /**
   * 生成文档 AI 摘要。
   *
   * <p>将原始文本传给 LLM，返回包含标题/摘要/关键词/分类的结构化分析结果。 调用 LLM 算力消耗大，本接口加 {@link RateLimit} 限流。
   *
   * @param content 文档正文（Markdown/纯文本）
   * @return 统一响应结果，data 为 {@link AiSummaryApplicationService.DocumentAnalysis}
   */
  @Idempotent(key = "ydsz:nextwiki:AnalysisController:analyze:lock", ttlSeconds = 5)
  @RateLimit(resource = "nextwiki.analysis.analyze", threshold = 50)
  @PostMapping("/summary")
  @Operation(summary = "生成文档摘要")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_ANALYSIS)
  public BaseResponse<AiSummaryApplicationService.DocumentAnalysis> analyze(
      @RequestBody String content) {
    return BaseResponse.success(aiSummaryService.analyze(content));
  }
}
