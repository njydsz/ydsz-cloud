package com.njydsz.nextwiki.web.controller;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.server.util.NextwikiFileUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.lock.annotation.Idempotent;

import com.njydsz.common.exception.custom.BusinessException;
import java.io.IOException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
/**
 * WOPI 协议接口（P1-4 + P1-R5 + P2-R4）
 * <p>
 * 集成 OnlyOffice / Collabora Online 在线协同编辑。
 * P1-R5: 增加 WOPI Token 验证 + 锁定状态检查。
 * P2-R4: 返回 DTO 替代 Map<String, Object>。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/wopi")
@RequiredArgsConstructor
@Tag(name = "WOPI 协议", description = "在线协同编辑 WOPI 接口（OnlyOffice/Collabora 集成）")
public class WopiController {

    private final FileNodeRepository fileNodeRepository;

    @Autowired(required = false)
    private IFileStorageProvider fileStorageProvider;

    @Value("${nextwiki.wopi.editor-url:}")
    private String editorUrl;

    @Value("${nextwiki.wopi.access-token:}")
    private String expectedAccessToken;

    /**
     * CheckFileInfo — 获取文件元信息
     */
    @GetMapping("/files/{fileId}")
    @Operation(summary = "WOPI CheckFileInfo", description = "返回文件元信息供在线编辑器使用")
    public WopiCheckFileInfoResponse checkFileInfo(
            @PathVariable String fileId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-WOPI-Authorization", required = false) String authToken) {

        // P1-R5: WOPI Token 验证
        validateWopiToken(authToken);

        FileNode fileNode = fileNodeRepository.findById(fileId);
        if (fileNode == null || !fileNode.isFile()) {
            return WopiCheckFileInfoResponse.error("file not found");
        }

        return WopiCheckFileInfoResponse.builder()
                .baseFileName(fileNode.getName())
                .ownerId(fileNode.getCreatedBy() != null ? fileNode.getCreatedBy() : "")
                .size(fileNode.getSize() != null ? fileNode.getSize() : 0)
                .userId(userId != null ? userId : "guest")
                .userFriendlyName(userId != null ? userId : "Guest")
                .version(fileNode.getCurrentVersion() != null ? fileNode.getCurrentVersion() : 1)
                .userCanWrite(true)
                .supportsUpdate(true)
                .supportsLocks(true)
                .lastModifiedTime(fileNode.getUpdatedAt() != null
                        ? fileNode.getUpdatedAt().toString() : LocalDateTime.now().toString())
                .build();
    }

    /**
     * GetFile — 获取文件内容
     */
    @GetMapping("/files/{fileId}/contents")
    @Operation(summary = "WOPI GetFile", description = "返回文件原始内容")
    public byte[] getFileContents(
            @PathVariable String fileId,
            @RequestHeader(value = "X-WOPI-Authorization", required = false) String authToken) {

        // P1-R5: WOPI Token 验证
        validateWopiToken(authToken);

        FileNode fileNode = fileNodeRepository.findById(fileId);
        if (fileNode == null || fileNode.getStorageKey() == null) {
            return new byte[0];
        }

        IFileStorage storage = resolveStorage();
        if (storage == null) {
            return new byte[0];
        }

        try {
            return storage.downloadAsStream(fileNode.getBucketName(), fileNode.getStorageKey())
                    .readAllBytes();
        } catch (Exception e) {
            log.error("[WopiController] GetFile 失败: fileId={}", fileId, e);
            return new byte[0];
        }
    }

    /**
     * PutFile — 保存文件内容
     */
    @Idempotent(key = "ydsz:nextwiki:WopiController:putFileContents:lock", ttlSeconds = 5)
    @PostMapping("/files/{fileId}/contents")
    @Operation(summary = "WOPI PutFile", description = "接收编辑器保存的文件内容")
    public WopiPutFileResponse putFileContents(
            @PathVariable String fileId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-WOPI-Authorization", required = false) String authToken,
            @RequestHeader(value = "X-WOPI-Lock", required = false) String lockId,
            @RequestBody byte[] content) {

        // P1-R5: WOPI Token 验证
        validateWopiToken(authToken);

        FileNode fileNode = fileNodeRepository.findById(fileId);
        if (fileNode == null) {
            return WopiPutFileResponse.error("file not found");
        }

        // P1-R5: 锁定状态检查——如果文件被锁定，只允许锁持有者保存
        if ("locked".equals(fileNode.getStatus()) && !userId.equals(fileNode.getUpdatedBy())) {
            log.warn("[WopiController] 文件被其他用户锁定，拒绝保存: fileId={}, lockedBy={}",
                    fileId, fileNode.getUpdatedBy());
            return WopiPutFileResponse.error("file is locked by another user");
        }

        IFileStorage storage = resolveStorage();
        if (storage == null) {
            return WopiPutFileResponse.error("storage not configured");
        }

        try {
            String storageKey = fileNode.getStorageKey();
            MultipartFile multipartFile =
                    NextwikiFileUtils.toMultipartFile(
                            writeTempFile(content), fileNode.getName(), fileNode.getMimeType());
            storage.upload(null, storageKey, multipartFile);

            fileNode.setSize((long) content.length);
            fileNode.setUpdatedBy(userId);
            fileNode.setUpdatedAt(LocalDateTime.now());
            fileNodeRepository.update(fileNode);

            log.info("[WopiController] PutFile 成功: fileId={}, size={}", fileId, content.length);
            return WopiPutFileResponse.ok();
        } catch (Exception e) {
            log.error("[WopiController] PutFile 失败: fileId={}", fileId, e);
            return WopiPutFileResponse.error(e.getMessage());
        }
    }

    /**
     * LockFile — 锁定文件
     */
    @Idempotent(key = "ydsz:nextwiki:WopiController:lockFile:lock", ttlSeconds = 5)
    @PostMapping("/files/{fileId}/lock")
    @Operation(summary = "WOPI Lock", description = "锁定文件防止并发编辑")
    public WopiPutFileResponse lockFile(
            @PathVariable String fileId,
            @RequestHeader(value = "X-WOPI-Lock", required = false) String lockId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-WOPI-Authorization", required = false) String authToken) {

        validateWopiToken(authToken);

        FileNode fileNode = fileNodeRepository.findById(fileId);
        if (fileNode == null) {
            return WopiPutFileResponse.error("file not found");
        }

        fileNode.setStatus("locked");
        fileNode.setUpdatedBy(userId);
        fileNode.setUpdatedAt(LocalDateTime.now());
        fileNodeRepository.update(fileNode);

        return WopiPutFileResponse.ok();
    }

    /**
     * UnlockFile — 解锁文件
     */
    @Idempotent(key = "ydsz:nextwiki:WopiController:unlockFile:lock", ttlSeconds = 5)
    @PostMapping("/files/{fileId}/unlock")
    @Operation(summary = "WOPI Unlock", description = "解锁文件")
    public WopiPutFileResponse unlockFile(
            @PathVariable String fileId,
            @RequestHeader(value = "X-WOPI-Lock", required = false) String lockId,
            @RequestHeader(value = "X-WOPI-Authorization", required = false) String authToken) {

        validateWopiToken(authToken);

        FileNode fileNode = fileNodeRepository.findById(fileId);
        if (fileNode == null) {
            return WopiPutFileResponse.error("file not found");
        }

        fileNode.setStatus("active");
        fileNodeRepository.update(fileNode);

        return WopiPutFileResponse.ok();
    }

    // ==================== 私有方法 ====================

    /**
     * P1-R5: WOPI Token 验证
     */
    private void validateWopiToken(String authToken) {
        if (expectedAccessToken != null && !expectedAccessToken.isEmpty()) {
            if (authToken == null || !authToken.equals(expectedAccessToken)) {
                throw new BusinessException(
                        com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode.FILE_NOT_FOUND);
            }
        }
    }

    private Path writeTempFile(byte[] content) throws IOException {
        Path tempFile = java.nio.file.Files.createTempFile("wopi-", ".tmp");
        java.nio.file.Files.write(tempFile, content);
        return tempFile;
    }

    private IFileStorage resolveStorage() {
        if (fileStorageProvider != null) {
            return fileStorageProvider.getStorage();
        }
        return null;
    }

    // ==================== DTO（P2-R4: 替代 Map<String, Object>） ====================

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    public static class WopiCheckFileInfoResponse {
        @lombok.Builder.Default
        private boolean error = false;
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

        public static WopiCheckFileInfoResponse error(String message) {
            return WopiCheckFileInfoResponse.builder()
                    .error(true).errorMessage(message).build();
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    public static class WopiPutFileResponse {
        @lombok.Builder.Default
        private boolean error = false;
        private String errorMessage;
        @lombok.Builder.Default
        private String status = "ok";

        public static WopiPutFileResponse ok() {
            return WopiPutFileResponse.builder().status("ok").build();
        }

        public static WopiPutFileResponse error(String message) {
            return WopiPutFileResponse.builder()
                    .error(true).errorMessage(message).status("error").build();
        }
    }
}
