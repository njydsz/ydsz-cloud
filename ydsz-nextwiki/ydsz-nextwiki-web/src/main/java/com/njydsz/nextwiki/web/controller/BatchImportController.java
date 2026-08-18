package com.njydsz.nextwiki.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.TagDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.nextwiki.server.service.BatchImportApplicationService;

/**
 * 批量导入 REST API Controller。
 *
 * <p>提供网盘文件的批量导入能力，是网盘"批量迁移/批量上传"特性的核心接口：
 *
 * <ul>
 *   <li>{@code POST /import/batch-upload} - 批量上传多个文件
 *   <li>{@code POST /import/zip} - 从 ZIP 压缩包导入（自动解压并按目录结构还原）
 * </ul>
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>批量上传：单次请求支持上传 N 个文件，自动创建文件节点 + 首版本
 *   <li>ZIP 导入：解压 ZIP 压缩包到指定目录，保留原始目录结构
 *   <li>部分成功：批量操作中部分文件失败不影响其他文件，结果中含 successCount / failCount / failures 详情
 *   <li>异步执行：大批量导入走异步任务，返回任务 ID，前端轮询进度
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 *
 * <ul>
 *   <li>所有写操作均加 {@link Idempotent} 防重（5s TTL）
 *   <li>所有写操作均加 {@link Audit} 异步落库审计日志（DATA 类型）
 *   <li>所有接口加 {@link AuthApiPermission} 权限码校验（NEXTWIKI_BATCH_IMPORT）
 *   <li>ZIP 解压路径校验：防止 Zip Slip 漏洞（{@code ../} 路径穿越）
 * </ul>
 *
 * <h3>接口路径</h3>
 *
 * <pre>
 *   POST /api/v1/nextwiki/import/batch-upload - 批量上传
 *   POST /api/v1/nextwiki/import/zip          - ZIP 导入
 * </pre>
 *
 * <h3>架构位置</h3>
 *
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-nextwiki-web (本 Controller)
 *                                            ↓
 *                                   ydsz-nextwiki-server.BatchImportApplicationService
 *                                            ↓
 *                                   ydsz-nextwiki-server.FileApplicationService (单文件复用)
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
@RequestMapping("/api/v1/nextwiki/import")
@RequiredArgsConstructor
@TagDO(name = "批量导入", description = "批量文件上传、ZIP 压缩包导入（自动解压保留目录结构）")
public class BatchImportController {

  /** 批量导入应用服务（封装批量上传 + ZIP 解压导入） */
  private final BatchImportApplicationService batchImportService;

  /**
   * 批量上传多个文件。
   *
   * <p>单次请求可上传 N 个文件，自动创建文件节点 + 首版本。 单个文件上传失败不会中断其他文件，结果中含每条的成功/失败状态。
   *
   * @param files 文件数组（{@code multipart/form-data}）
   * @param parentId 父目录 ID（可空表示根目录）
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为 {@link BatchImportApplicationService.BatchImportResult}
   */
  @Audit(
      module = "批量导入",
      type = AuditType.DATA,
      action = AuditAction.CREATE,
      content = "'batchUpload'")
  @Idempotent(key = "ydsz:nextwiki:BatchImportController:batchUpload:lock", ttlSeconds = 5)
  @PostMapping("/batch-upload")
  @Operation(summary = "批量上传文件")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_BATCH_IMPORT)
  public BaseResponse<BatchImportApplicationService.BatchImportResult> batchUpload(
      @RequestParam("files") MultipartFile[] files,
      @RequestParam(value = "parentId", required = false) String parentId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
    return BaseResponse.success(batchImportService.batchUpload(files, parentId, userId));
  }

  /**
   * 从 ZIP 压缩包导入文件。
   *
   * <p>解压 ZIP 到指定目录，保留原始目录结构（嵌套文件夹会一并创建）。 自动校验路径防 Zip Slip 漏洞。
   *
   * @param zipFile ZIP 压缩包
   * @param parentId 父目录 ID（可空表示根目录）
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为 {@link BatchImportApplicationService.BatchImportResult}
   */
  @Audit(
      module = "批量导入",
      type = AuditType.DATA,
      action = AuditAction.CREATE,
      content = "'importZip'")
  @Idempotent(key = "ydsz:nextwiki:BatchImportController:importZip:lock", ttlSeconds = 5)
  @PostMapping("/zip")
  @Operation(summary = "从 ZIP 压缩包导入")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_BATCH_IMPORT)
  public BaseResponse<BatchImportApplicationService.BatchImportResult> importZip(
      @RequestParam("file") MultipartFile zipFile,
      @RequestParam(value = "parentId", required = false) String parentId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
    return BaseResponse.success(batchImportService.importFromZip(zipFile, parentId, userId));
  }
}
