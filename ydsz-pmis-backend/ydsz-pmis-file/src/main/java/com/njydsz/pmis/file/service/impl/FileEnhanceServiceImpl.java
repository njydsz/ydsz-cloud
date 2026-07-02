package com.njydsz.pmis.file.service.impl;

import com.njydsz.pmis.file.service.FileEnhanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 文件增强服务实现。
 *
 * <ul>
 *   <li>文件类型白名单：图片/文档/压缩包</li>
 *   <li>文件大小限制：默认 100MB</li>
 *   <li>病毒扫描：预留 ClamAV 接口，当前返回 true（安全）</li>
 *   <li>分片上传：内存缓存分片，合并后上传 MinIO</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Service
public class FileEnhanceServiceImpl implements FileEnhanceService {

    private static final Logger log = LoggerFactory.getLogger(FileEnhanceServiceImpl.class);

    /** 允许的文件扩展名白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "csv", "md",
            "zip", "rar", "7z", "gz"
    );

    /** 允许的 MIME 类型白名单 */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/bmp",
            "application/pdf",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain", "text/csv", "text/markdown",
            "application/zip", "application/x-rar-compressed", "application/x-7z-compressed",
            "application/gzip"
    );

    /** 最大文件大小（字节），默认 100MB */
    @Value("${file.max-size:104857600}")
    private long maxFileSize;

    /** 分片缓存：uploadId → 分片数据列表 */
    private final Map<String, List<byte[]>> chunkStore = new HashMap<>();

    /** 分片元信息：uploadId → filename:totalSize:totalChunks */
    private final Map<String, String> chunkMeta = new HashMap<>();

    @Override
    public boolean validateFileType(String filename, String contentType) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        boolean extOk = ALLOWED_EXTENSIONS.contains(ext);
        boolean ctOk = contentType == null || ALLOWED_CONTENT_TYPES.contains(contentType);
        return extOk && ctOk;
    }

    @Override
    public boolean validateFileSize(long fileSize, long maxSize) {
        return fileSize > 0 && fileSize <= maxSize;
    }

    @Override
    public boolean scanVirus(MultipartFile file) {
        // TODO: 对接 ClamAV daemon (clamd INSTREAM)
        // 当前返回 true（安全），生产环境必须接入 ClamAV
        try {
            byte[] bytes = file.getBytes();
            log.debug("[FileVirusScan] 文件 {} 大小 {} 字节，扫描通过（mock）",
                    file.getOriginalFilename(), bytes.length);
            return true;
        } catch (IOException e) {
            log.warn("[FileVirusScan] 文件读取失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public synchronized String initMultipartUpload(String filename, long totalSize, int totalChunks) {
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        chunkStore.put(uploadId, new ArrayList<>(Collections.nCopies(totalChunks, null)));
        chunkMeta.put(uploadId, filename + ":" + totalSize + ":" + totalChunks);
        log.info("[MultipartUpload] 初始化分片上传: uploadId={}, filename={}, chunks={}",
                uploadId, filename, totalChunks);
        return uploadId;
    }

    @Override
    public synchronized boolean uploadChunk(String uploadId, int chunkIndex, byte[] chunkData) {
        List<byte[]> chunks = chunkStore.get(uploadId);
        if (chunks == null || chunkIndex < 0 || chunkIndex >= chunks.size()) {
            log.warn("[MultipartUpload] 无效的分片上传: uploadId={}, chunkIndex={}", uploadId, chunkIndex);
            return false;
        }
        chunks.set(chunkIndex, chunkData);
        log.debug("[MultipartUpload] 分片上传成功: uploadId={}, chunk={}/{}",
                uploadId, chunkIndex + 1, chunks.size());
        return true;
    }

    @Override
    public synchronized String completeMultipartUpload(String uploadId) {
        List<byte[]> chunks = chunkStore.get(uploadId);
        if (chunks == null || chunks.stream().anyMatch(Objects::isNull)) {
            log.warn("[MultipartUpload] 分片不完整，无法合并: uploadId={}", uploadId);
            return null;
        }
        // 合并分片
        int totalLength = chunks.stream().mapToInt(c -> c.length).sum();
        byte[] merged = new byte[totalLength];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, merged, offset, chunk.length);
            offset += chunk.length;
        }
        String meta = chunkMeta.get(uploadId);
        String filename = meta != null ? meta.split(":")[0] : "unknown";
        String fileKey = uploadId + "/" + filename;
        log.info("[MultipartUpload] 分片合并完成: uploadId={}, fileKey={}, size={}",
                uploadId, fileKey, totalLength);
        // 清理分片缓存
        chunkStore.remove(uploadId);
        chunkMeta.remove(uploadId);
        // TODO: 将 merged 上传到 MinIO
        return fileKey;
    }

    @Override
    public synchronized void abortMultipartUpload(String uploadId) {
        chunkStore.remove(uploadId);
        chunkMeta.remove(uploadId);
        log.info("[MultipartUpload] 取消分片上传: uploadId={}", uploadId);
    }

    @Override
    public String generatePreviewUrl(String fileKey) {
        // TODO: 对接 kkFileView 或 OnlyOffice
        // 当前返回 MinIO 预签名 URL 的占位
        String previewUrl = "/api/v1/file/preview/" + fileKey;
        log.debug("[FilePreview] 生成预览URL: fileKey={}, url={}", fileKey, previewUrl);
        return previewUrl;
    }
}
