package com.njydsz.nextwiki.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.nextwiki.server.service.PreviewApplicationService;

/**
 * 文档预览 REST API Controller。
 *
 * <p>提供在线文档预览能力，是网盘"在线打开"功能的核心入口：
 *
 * <ul>
 *   <li>{@code POST /preview/{fileNodeId}/generate} - 异步生成预览文件（PDF/图片/HTML 等）
 *   <li>{@code GET /preview/supported?suffix=xxx} - 判断指定后缀是否支持预览
 *   <li>{@code GET /preview/type?suffix=xxx} - 获取指定后缀的预览类型（pdf/image/text/code）
 * </ul>
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>异步预览：生成预览耗时长（Office 转换、PDF 渲染），采用异步任务 + 轮询/推送机制
 *   <li>格式识别：基于文件后缀判断是否支持预览及预览类型
 *   <li>支持 Office（doc/docx/xls/xlsx/ppt/pptx）/PDF/图片/文本/代码等常见格式
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 *
 * <ul>
 *   <li>预览生成是 CPU/IO 密集型操作，加 {@link RateLimit} 限流（50 QPS）防过载
 *   <li>所有写操作加 {@link Idempotent} 防重（5s TTL）
 *   <li>所有接口加 {@link AuthApiPermission} 权限码校验（NEXTWIKI_PREVIEW_*）
 * </ul>
 *
 * <h3>接口路径</h3>
 *
 * <pre>
 *   POST /api/v1/nextwiki/preview/{fileNodeId}/generate - 异步生成预览
 *   GET  /api/v1/nextwiki/preview/supported?suffix=pdf  - 是否支持预览
 *   GET  /api/v1/nextwiki/preview/type?suffix=pdf       - 预览类型
 * </pre>
 *
 * <h3>架构位置</h3>
 *
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-nextwiki-web (本 Controller)
 *                                            ↓
 *                                   ydsz-nextwiki-server.PreviewApplicationService
 *                                            ↓
 *                                   ydsz-nextwiki-server.DocumentConversionApplicationService
 *                                   ydsz-nextwiki-server.ThumbnailApplicationService
 *                                            ↓
 *                                   ydsz-nextwiki-infra Mapper
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ApiVersion("v1")
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/preview")
@RequiredArgsConstructor
@Tag(name = "文档预览", description = "在线预览生成、缩略图、文档格式转换")
public class PreviewController {

  /** 预览应用服务（封装预览生成 + 类型判断 + 后缀识别） */
  private final PreviewApplicationService previewService;

  /**
   * 异步生成预览文件。
   *
   * <p>对于 Office/PDF 等需要转换的格式，本接口为异步触发：实际转换任务由后台异步执行， 客户端通过 {@code fileNodeId} 轮询预览状态或订阅 SSE 推送。
   *
   * @param fileNodeId 文件节点 ID
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:nextwiki:PreviewController:generatePreview:lock", ttlSeconds = 5)
  @RateLimit(resource = "nextwiki.preview.generatePreview", threshold = 50)
  @PostMapping("/{fileNodeId}/generate")
  @Operation(summary = "生成预览（异步）")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_PREVIEW_GENERATE)
  public BaseResponse<Void> generatePreview(@PathVariable String fileNodeId) {
    previewService.generatePreview(fileNodeId);
    return BaseResponse.success();
  }

  /**
   * 判断指定后缀是否支持预览。
   *
   * <p>前端在用户点击"预览"前预先检查，避免对不支持的格式发起无效的预览生成请求。
   *
   * @param suffix 文件后缀（不含点号，如 {@code pdf}、{@code docx}）
   * @return 统一响应结果，data 为 true 表示支持预览
   */
  @GetMapping("/supported")
  @Operation(summary = "检查文件是否支持预览")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_PREVIEW_VIEW)
  public BaseResponse<Boolean> isSupported(@RequestParam String suffix) {
    return BaseResponse.success(previewService.isPreviewSupported(suffix));
  }

  /**
   * 获取指定后缀的预览类型。
   *
   * <p>返回的预览类型决定前端使用哪种渲染组件：
   *
   * <ul>
   *   <li>{@code pdf} - PDF 渲染器（pdf.js）
   *   <li>{@code image} - 图片预览组件
   *   <li>{@code text} - 文本预览（语法高亮）
   *   <li>{@code video} - 视频播放器
   *   <li>{@code audio} - 音频播放器
   *   <li>{@code code} - 代码编辑器只读模式
   *   <li>{@code none} - 不支持
   * </ul>
   *
   * @param suffix 文件后缀
   * @return 统一响应结果，data 为预览类型字符串
   */
  @GetMapping("/type")
  @Operation(summary = "获取预览类型")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_PREVIEW_VIEW)
  public BaseResponse<String> getPreviewType(@RequestParam String suffix) {
    return BaseResponse.success(previewService.getPreviewType(suffix));
  }
}
