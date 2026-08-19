package com.njydsz.nextwiki.web.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.common.file.util.FileOps;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.converter.NextwikiConverter;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.server.config.NextwikiProperties;
import org.springframework.transaction.annotation.Transactional;

/**
 * WOPI 协议接口 Controller（P1-4 + P1-R5 + P2-R4）。
 *
 * <p>实现 WOPI（Web Application Open Platform Interface）协议，对接 OnlyOffice / Collabora Online
 * 等在线协同编辑器，是网盘"在线编辑 Word/Excel/PPT"能力的核心接口：
 *
 * <ul>
 *   <li>{@code GET /wopi/files/{fileId}} - CheckFileInfo：返回文件元信息
 *   <li>{@code GET /wopi/files/{fileId}/contents} - GetFile：返回文件原始内容
 *   <li>{@code POST /wopi/files/{fileId}/contents} - PutFile：保存编辑器内容
 *   <li>{@code POST /wopi/files/{fileId}/lock} - Lock：锁定文件防并发
 *   <li>{@code POST /wopi/files/{fileId}/unlock} - Unlock：解锁文件
 * </ul>
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>P1-R5 修复：增加 WOPI Token 验证（{@code X-WOPI-Authorization}）+ 锁定状态校验
 *   <li>P2-R4 修复：使用 DTO（{@link WopiCheckFileInfoResponse} / {@link WopiPutFileResponse}）替代 Map
 *   <li>支持 OnlyOffice Document Server / Collabora Online 等 WOPI 兼容编辑器
 *   <li>PUT 请求支持锁定持有者校验，防并发编辑冲突
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 *
 * <ul>
 *   <li>所有写操作加 {@link Idempotent} 防重（5s TTL）
 *   <li>所有写操作加 WOPI Token 验证（{@code expectedAccessToken} 配置）
 *   <li>PutFile 增加锁定持有者校验，非锁持有者保存会被拒绝
 *   <li>异常处理：内部异常转 WOPI 错误响应，不泄露堆栈
 * </ul>
 *
 * <h3>接口路径</h3>
 *
 * <pre>
 *   GET  /api/v1/nextwiki/wopi/files/{fileId}                - CheckFileInfo
 *   GET  /api/v1/nextwiki/wopi/files/{fileId}/contents      - GetFile
 *   POST /api/v1/nextwiki/wopi/files/{fileId}/contents      - PutFile
 *   POST /api/v1/nextwiki/wopi/files/{fileId}/lock          - Lock
 *   POST /api/v1/nextwiki/wopi/files/{fileId}/unlock        - Unlock
 * </pre>
 *
 * <h3>架构位置</h3>
 *
 * <pre>
 *   OnlyOffice/Collabora 编辑器
 *     → ydsz-gateway
 *       → ydsz-nextwiki-web (本 Controller)
 *         → ydsz-nextwiki-domain.FileNodeRepository
 *         → ydsz-common-file (IFileStorage 抽象)
 * → ydsz-common-file.FileOps
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ApiVersion("v1")
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/wopi")
@RequiredArgsConstructor
@Tag(name = "WOPI 协议", description = "在线协同编辑 WOPI 接口（OnlyOffice / Collabora 集成）")
public class WopiController {

  /** 文件节点仓储（用于查询/更新文件） */
  private final FileNodeRepository fileNodeRepository;

  /** NextWiki 全局配置 */
  private final NextwikiProperties properties;

  /** 文件存储提供者（optional，可能不存在于所有部署环境） */
  @Autowired(required = false)
  private IFileStorageProvider fileStorageProvider;

  /** CheckFileInfo — 获取文件元信息 */
  @GetMapping("/files/{fileId}")
  @Operation(summary = "WOPI CheckFileInfo", description = "返回文件元信息供在线编辑器使用")
  public WopiCheckFileInfoResponse checkFileInfo(
      @PathVariable String fileId,
      @RequestHeader(value = AuthHeaderConstants.X_USER_ID, required = false) String userId,
      @RequestHeader(value = "X-WOPI-Authorization", required = false) String authToken) {

    // P1-R5: WOPI Token 验证
    validateWopiToken(authToken);

    FileNodeVO node = fileNodeRepository.findById(fileId).orElse(null);
    if (node == null || !node.isFile()) {
      return WopiCheckFileInfoResponse.error("file not found");
    }

    return WopiCheckFileInfoResponse.builder()
        .baseFileName(node.getName())
        .ownerId(node.getCreatedBy() != null ? node.getCreatedBy() : "")
        .size(node.getSize() != null ? node.getSize() : 0)
        .userId(userId != null ? userId : "guest")
        .userFriendlyName(userId != null ? userId : "Guest")
        .version(node.getCurrentVersion() != null ? node.getCurrentVersion() : 1)
        .userCanWrite(true)
        .supportsUpdate(true)
        .supportsLocks(true)
        .lastModifiedTime(
            node.getUpdatedAt() != null
                ? node.getUpdatedAt().toString()
                : LocalDateTime.now().toString())
        .build();
  }

  /** GetFile — 获取文件内容 */
  @GetMapping("/files/{fileId}/contents")
  @Operation(summary = "WOPI GetFile", description = "返回文件原始内容")
  public byte[] getFileContents(
      @PathVariable String fileId,
      @RequestHeader(value = "X-WOPI-Authorization", required = false) String authToken) {

    // P1-R5: WOPI Token 验证
    validateWopiToken(authToken);

    FileNodeVO node = fileNodeRepository.findById(fileId).orElse(null);
    if (node == null || node.getStorageKey() == null) {
      return new byte[0];
    }

    IFileStorage storage = resolveStorage();
    if (storage == null) {
      return new byte[0];
    }

    try {
      return storage
          .downloadAsStream(node.getBucketName(), node.getStorageKey())
          .readAllBytes();
    } catch (Exception e) {
      log.error("[WopiController] GetFile 失败: fileId={}", fileId, e);
      return new byte[0];
    }
  }

  /** PutFile — 保存文件内容 */
  @Idempotent(key = "ydsz:nextwiki:WopiController:putFileContents:lock", ttlSeconds = 5)
  @PostMapping("/files/{fileId}/contents")
  @Operation(summary = "WOPI PutFile", description = "接收编辑器保存的文件内容")
  @Transactional(rollbackFor = Exception.class)
  public WopiPutFileResponse putFileContents(
      @PathVariable String fileId,
      @RequestHeader(value = AuthHeaderConstants.X_USER_ID, required = false) String userId,
      @RequestHeader(value = "X-WOPI-Authorization", required = false) String authToken,
      @RequestHeader(value = "X-WOPI-Lock", required = false) String lockId,
      @RequestBody byte[] content) {

    // P1-R5: WOPI Token 验证
    validateWopiToken(authToken);

    FileNodeVO node = fileNodeRepository.findById(fileId).orElse(null);
    if (node == null) {
      return WopiPutFileResponse.error("file not found");
    }

    // P1-R5: 锁定状态检查——如果文件被锁定，只允许锁持有者保存
    if ("locked".equals(node.getStatus()) && !userId.equals(node.getUpdatedBy())) {
      log.warn(
          "[WopiController] 文件被其他用户锁定，拒绝保存: fileId={}, lockedBy={}",
          fileId,
          node.getUpdatedBy());
      return WopiPutFileResponse.error("file is locked by another user");
    }

    IFileStorage storage = resolveStorage();
    if (storage == null) {
      return WopiPutFileResponse.error("storage not configured");
    }

    try {
      String storageKey = node.getStorageKey();
      MultipartFile multipartFile =
          FileOps.toMultipartFile(
              writeTempFile(content), node.getName(), node.getMimeType());
      storage.upload(null, storageKey, multipartFile);

      node.setSize((long) content.length);
      node.setUpdatedBy(userId);
      node.setUpdatedAt(LocalDateTime.now());
      fileNodeRepository.update(NextwikiConverter.INSTANT.toDTO(node));

      log.info("[WopiController] PutFile 成功: fileId={}, size={}", fileId, content.length);
      return WopiPutFileResponse.ok();
    } catch (Exception e) {
      log.error("[WopiController] PutFile 失败: fileId={}", fileId, e);
      return WopiPutFileResponse.error(e.getMessage());
    }
  }

  /** LockFile — 锁定文件 */
  @Idempotent(key = "ydsz:nextwiki:WopiController:lockFile:lock", ttlSeconds = 5)
  @PostMapping("/files/{fileId}/lock")
  @Operation(summary = "WOPI Lock", description = "锁定文件防止并发编辑")
  @Transactional(rollbackFor = Exception.class)
  public WopiPutFileResponse lockFile(
      @PathVariable String fileId,
      @RequestHeader(value = "X-WOPI-Lock", required = false) String lockId,
      @RequestHeader(value = AuthHeaderConstants.X_USER_ID, required = false) String userId,
      @RequestHeader(value = "X-WOPI-Authorization", required = false) String authToken) {

    validateWopiToken(authToken);

    FileNodeVO node = fileNodeRepository.findById(fileId).orElse(null);
    if (node == null) {
      return WopiPutFileResponse.error("file not found");
    }

    node.setStatus("locked");
    node.setUpdatedBy(userId);
    node.setUpdatedAt(LocalDateTime.now());
    fileNodeRepository.update(NextwikiConverter.INSTANT.toDTO(node));

    return WopiPutFileResponse.ok();
  }

  /** UnlockFile — 解锁文件 */
  @Idempotent(key = "ydsz:nextwiki:WopiController:unlockFile:lock", ttlSeconds = 5)
  @PostMapping("/files/{fileId}/unlock")
  @Operation(summary = "WOPI Unlock", description = "解锁文件")
  @Transactional(rollbackFor = Exception.class)
  public WopiPutFileResponse unlockFile(
      @PathVariable String fileId,
      @RequestHeader(value = "X-WOPI-Lock", required = false) String lockId,
      @RequestHeader(value = "X-WOPI-Authorization", required = false) String authToken) {

    validateWopiToken(authToken);

    FileNodeVO node = fileNodeRepository.findById(fileId).orElse(null);
    if (node == null) {
      return WopiPutFileResponse.error("file not found");
    }

    node.setStatus("active");
    fileNodeRepository.update(NextwikiConverter.INSTANT.toDTO(node));

    return WopiPutFileResponse.ok();
  }

  // ==================== 私有方法 ====================

  /** P1-R5: WOPI Token 验证 */
  private void validateWopiToken(String authToken) {
    String expectedAccessToken = properties.getWopi().getAccessToken();
    if (expectedAccessToken != null && !expectedAccessToken.isEmpty()) {
      if (authToken == null
          || !MessageDigest.isEqual(authToken.getBytes(), expectedAccessToken.getBytes())) {
        throw new BusinessException(NextwikiExceptionCode.FILE_NOT_FOUND);
      }
    }
  }

  private Path writeTempFile(byte[] content) throws IOException {
    Path tempFile = Files.createTempFile("wopi-", ".tmp");
    Files.write(tempFile, content);
    return tempFile;
  }

  private IFileStorage resolveStorage() {
    if (fileStorageProvider != null) {
      return fileStorageProvider.getStorage();
    }
    return null;
  }

  // ==================== DTO（P2-R4: 替代 Map<String, Object>） ====================

  /**
   * WOPI CheckFileInfo 响应体。
   *
   * <p>字段名<b>必须严格遵循 WOPI 规范的驼峰命名</b>（{@code BaseFileName}、 {@code OwnerId}
   * 等由序列化层映射），编辑器按名取值，重命名会直接导致文档打不开。
   *
   * <p>{@code userCanWrite} / {@code supportsUpdate} / {@code supportsLocks} 共同决定
   * 编辑器展现只读还是可编辑模式；{@code version} 变化会触发编辑器重新拉取内容， 因此内容更新后务必同步递增，否则用户会看到陈旧文档。
   *
   * @author ydsz-team
   * @since 1.0.0
   */
  @lombok.Data
  @lombok.Builder
  @lombok.AllArgsConstructor
  public static class WopiCheckFileInfoResponse {
    @lombok.Builder.Default private boolean error = false;
    private String errorMessage;
    private String baseFileName;
    private String ownerId;
    private long size;
    private String userId;
    private String userFriendlyName;
    private int version;
    private boolean userCanWrite;
    private boolean supportsUpdate;
    private boolean supportsLocks;
    private String lastModifiedTime;

    /**
     * 构造 CheckFileInfo 的错误响应。
     *
     * <p>WOPI 客户端（OnlyOffice / Collabora）依据响应体判定是否放弃打开文档， 因此文件不存在、存储未配置等场景返回<b>带 error 标记的 200
     * 响应</b>而非抛异常， 避免编辑器把 5xx 当作宿主故障持续重试。
     *
     * <p><b>安全约定：</b>{@code message} 只写对外可见的简短英文原因 （如 {@code "file not found"}），严禁回传堆栈或内部路径。 除
     * {@code error}/{@code errorMessage} 外其余字段均为默认值，客户端不应读取。
     *
     * @param message 对外错误原因
     * @return 错误响应，{@code error=true}
     */
    public static WopiCheckFileInfoResponse error(String message) {
      return WopiCheckFileInfoResponse.builder().error(true).errorMessage(message).build();
    }
  }

  /**
   * WOPI 写操作（PutFile / Lock / Unlock）统一响应体。
   *
   * <p>三类写接口复用同一结构，便于编辑器以一致方式解析。约定即使失败也返回 HTTP 200，由 {@code error} 标记区分成败——WOPI 客户端遇 5xx 会重试或丢弃
   * 用户改动，返回 200 + error 可让其保留本地副本并给出明确提示。
   *
   * @author ydsz-team
   * @since 1.0.0
   */
  @lombok.Data
  @lombok.Builder
  @lombok.AllArgsConstructor
  public static class WopiPutFileResponse {
    @lombok.Builder.Default private boolean error = false;
    private String errorMessage;
    @lombok.Builder.Default private String status = "ok";

    /**
     * 构造写操作成功响应，供 PutFile / Lock / Unlock 共用。
     *
     * <p>{@code status} 固定为 {@code "ok"}，WOPI 客户端据此结束保存流程； 若返回非 ok，编辑器会保留本地未保存副本并提示用户。
     *
     * @return 成功响应，{@code error=false}、{@code status="ok"}
     */
    public static WopiPutFileResponse ok() {
      return WopiPutFileResponse.builder().status("ok").build();
    }

    /**
     * 构造写操作失败响应。
     *
     * <p>覆盖三类失败：文件不存在、<b>被其他用户锁定</b>（非锁持有者保存被拒， 用于防并发覆盖）、存储不可用或上传异常。同样以 200 + error 标记返回，
     * 让编辑器保留用户改动而不是直接丢弃。
     *
     * <p><b>安全约定：</b>异常分支传入的是 {@code e.getMessage()}， 已由上层保证不含堆栈；新增调用点须同样避免泄露内部细节。
     *
     * @param message 对外错误原因
     * @return 失败响应，{@code error=true}、{@code status="error"}
     */
    public static WopiPutFileResponse error(String message) {
      return WopiPutFileResponse.builder()
          .error(true)
          .errorMessage(message)
          .status("error")
          .build();
    }
  }
}
