package com.njydsz.nextwiki.web.controller;

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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WOPI 协议接口（P1-4）
 * <p>
 * 集成 OnlyOffice / Collabora Online 在线协同编辑。
 * WOPI（Web Application Open Platform Interface）是标准协议，
 * 允许在线文档编辑器与存储后端交互。
 *
 * <p><b>WOPI 端点：</b>
 * <ul>
 *   <li>GET /wopi/files/{id} — 获取文件元信息（CheckFileInfo）</li>
 *   <li>GET /wopi/files/{id}/contents — 获取文件内容（GetFile）</li>
 *   <li>POST /wopi/files/{id}/contents — 保存文件内容（PutFile）</li>
 *   <li>POST /wopi/files/{id}/lock — 锁定文件</li>
 *   <li>POST /wopi/files/{id}/unlock — 解锁文件</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.4.0
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

    /**
     * CheckFileInfo — 获取文件元信息
     */
    @GetMapping("/files/{fileId}")
    @Operation(summary = "WOPI CheckFileInfo", description = "返回文件元信息供在线编辑器使用")
    public Map<String, Object> checkFileInfo(
            @PathVariable String fileId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        FileNode fileNode = fileNodeRepository.findById(fileId);
        if (fileNode == null || !fileNode.isFile()) {
            return Map.of("error", "file not found");
        }

        return Map.of(
                "BaseFileName", fileNode.getName(),
                "OwnerId", fileNode.getCreatedBy() != null ? fileNode.getCreatedBy() : "",
                "Size", fileNode.getSize() != null ? fileNode.getSize() : 0,
                "UserId", userId != null ? userId : "guest",
                "UserFriendlyName", userId != null ? userId : "Guest",
                "Version", fileNode.getCurrentVersion() != null ? fileNode.getCurrentVersion() : 1,
                "UserCanWrite", true,
                "SupportsUpdate", true,
                "SupportsLocks", true,
                "LastModifiedTime", fileNode.getUpdatedAt() != null
                        ? fileNode.getUpdatedAt().toString() : LocalDateTime.now().toString()
        );
    }

    /**
     * GetFile — 获取文件内容
     */
    @GetMapping("/files/{fileId}/contents")
    @Operation(summary = "WOPI GetFile", description = "返回文件原始内容")
    public byte[] getFileContents(@PathVariable String fileId) {
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
    @PostMapping("/files/{fileId}/contents")
    @Operation(summary = "WOPI PutFile", description = "接收编辑器保存的文件内容")
    public Map<String, Object> putFileContents(
            @PathVariable String fileId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @org.springframework.web.bind.annotation.RequestBody byte[] content) {

        FileNode fileNode = fileNodeRepository.findById(fileId);
        if (fileNode == null) {
            return Map.of("error", "file not found");
        }

        IFileStorage storage = resolveStorage();
        if (storage == null) {
            return Map.of("error", "storage not configured");
        }

        try {
            // 上传新版本
            String storageKey = fileNode.getStorageKey();
            org.springframework.web.multipart.MultipartFile multipartFile =
                    new org.springframework.web.multipart.MultipartFile() {
                        @Override public String getName() { return "content"; }
                        @Override public String getOriginalFilename() { return fileNode.getName(); }
                        @Override public String getContentType() { return fileNode.getMimeType(); }
                        @Override public boolean isEmpty() { return content.length == 0; }
                        @Override public long getSize() { return content.length; }
                        @Override public byte[] getBytes() { return content; }
                        @Override public java.io.InputStream getInputStream() {
                            return new java.io.ByteArrayInputStream(content);
                        }
                        @Override public void transferTo(java.io.File dest) throws java.io.IOException {
                            java.nio.file.Files.write(dest.toPath(), content);
                        }
                    };
            storage.upload(null, storageKey, multipartFile);

            fileNode.setSize((long) content.length);
            fileNode.setUpdatedBy(userId);
            fileNode.setUpdatedAt(LocalDateTime.now());
            fileNodeRepository.update(fileNode);

            log.info("[WopiController] PutFile 成功: fileId={}, size={}", fileId, content.length);
            return Map.of("status", "ok");
        } catch (Exception e) {
            log.error("[WopiController] PutFile 失败: fileId={}", fileId, e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * LockFile — 锁定文件（编辑器获取编辑权时调用）
     */
    @PostMapping("/files/{fileId}/lock")
    @Operation(summary = "WOPI Lock", description = "锁定文件防止并发编辑")
    public Map<String, Object> lockFile(
            @PathVariable String fileId,
            @RequestHeader(value = "X-WOPI-Lock", required = false) String lockId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        FileNode fileNode = fileNodeRepository.findById(fileId);
        if (fileNode == null) {
            return Map.of("error", "file not found");
        }

        // 简单实现：在 FileNode 上记录锁定信息
        fileNode.setStatus("locked");
        fileNodeRepository.update(fileNode);

        return Map.of("status", "ok", "lockId", lockId != null ? lockId : "");
    }

    /**
     * UnlockFile — 解锁文件（编辑器保存或关闭时调用）
     */
    @PostMapping("/files/{fileId}/unlock")
    @Operation(summary = "WOPI Unlock", description = "解锁文件")
    public Map<String, Object> unlockFile(
            @PathVariable String fileId,
            @RequestHeader(value = "X-WOPI-Lock", required = false) String lockId) {

        FileNode fileNode = fileNodeRepository.findById(fileId);
        if (fileNode == null) {
            return Map.of("error", "file not found");
        }

        fileNode.setStatus("active");
        fileNodeRepository.update(fileNode);

        return Map.of("status", "ok");
    }

    private IFileStorage resolveStorage() {
        if (fileStorageProvider != null) {
            return fileStorageProvider.getStorage();
        }
        return null;
    }
}
