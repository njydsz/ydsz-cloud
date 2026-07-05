package com.njydsz.pmis.system.service.impl;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.system.config.MinioConfig;
import com.njydsz.pmis.system.service.FileEnhanceService;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 文件增强服务实现。
 *
 * <ul>
 *   <li>文件类型白名单：图片/文档/压缩包</li>
 *   <li>文件大小限制：默认 100MB</li>
 *   <li>病毒扫描：对接 ClamAV daemon（clamd INSTREAM 协议），连接失败 fail-open 放行</li>
 *   <li>分片上传：分片元数据存 Redis、分片数据存 MinIO 临时对象，合并后上传 MinIO</li>
 *   <li>在线预览：MinIO 预签名 URL（30 分钟），配置 kkFileView 时返回 kkFileView 地址</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileEnhanceServiceImpl implements FileEnhanceService {

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

    /** ClamAV INSTREAM 单次发送分片大小（字节） */
    private static final int CLAMAV_CHUNK_SIZE = 2048;
    /** ClamAV 连接超时（毫秒） */
    private static final int CLAMAV_CONNECT_TIMEOUT = 5000;
    /** ClamAV 读取超时（毫秒） */
    private static final int CLAMAV_READ_TIMEOUT = 10000;
    /** INSTREAM 协议握手指令 */
    private static final String CLAMAV_INSTREAM_CMD = "zINSTREAM\u0000";
    /** 预签名预览 URL 过期时间（分钟） */
    private static final int PREVIEW_EXPIRE_MINUTES = 30;

    /** 分片元数据 Redis 前缀 */
    private static final String META_KEY_PREFIX = "multipart:meta:";
    /** 已上传分片索引 Redis Set 前缀 */
    private static final String CHUNKS_KEY_PREFIX = "multipart:chunks:";
    /** MinIO 临时分片对象前缀 */
    private static final String MULTIPART_PREFIX = "multipart/";

    /** 最大文件大小（字节），默认 100MB */
    @Value("${file.max-size:104857600}")
    private long maxFileSize;

    /** ClamAV 主机 */
    @Value("${CLAMAV_HOST:127.0.0.1}")
    private String clamavHost;

    /** ClamAV 端口 */
    @Value("${CLAMAV_PORT:3310}")
    private int clamavPort;

    /** kkFileView 在线预览地址（可选） */
    @Value("${KKFILEVIEW_URL:}")
    private String kkFileViewUrl;

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final StringRedisTemplate stringRedisTemplate;

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
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.warn("[FileVirusScan] 文件读取失败: {}", e.getMessage());
            return false;
        }
        return scanWithClamAv(bytes, file.getOriginalFilename());
    }

    @Override
    public String initMultipartUpload(String filename, long totalSize, int totalChunks) {
        String uploadId = SnowflakeIdGenerator.nextIdStr();
        ChunkMeta meta = new ChunkMeta(filename, totalSize, totalChunks);
        try {
            stringRedisTemplate.opsForValue().set(metaKey(uploadId), JSON.toJSONString(meta));
        } catch (Exception e) {
            log.warn("[MultipartUpload] 写入分片元数据到 Redis 失败, uploadId={}: {}", uploadId, e.getMessage());
        }
        log.info("[MultipartUpload] 初始化分片上传: uploadId={}, filename={}, chunks={}",
                uploadId, filename, totalChunks);
        return uploadId;
    }

    @Override
    public boolean uploadChunk(String uploadId, int chunkIndex, byte[] chunkData) {
        ChunkMeta meta = loadMeta(uploadId);
        if (meta == null || chunkIndex < 0 || chunkIndex >= meta.getTotalChunks()) {
            log.warn("[MultipartUpload] 无效的分片上传: uploadId={}, chunkIndex={}", uploadId, chunkIndex);
            return false;
        }
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket())
                    .object(tempChunkKey(uploadId, chunkIndex))
                    .stream(new ByteArrayInputStream(chunkData), chunkData.length, -1)
                    .contentType("application/octet-stream")
                    .build());
        } catch (Exception e) {
            log.warn("[MultipartUpload] 分片上传到 MinIO 失败: uploadId={}, chunkIndex={}: {}",
                    uploadId, chunkIndex, e.getMessage());
            return false;
        }
        try {
            stringRedisTemplate.opsForSet().add(chunksKey(uploadId), String.valueOf(chunkIndex));
        } catch (Exception e) {
            log.warn("[MultipartUpload] 记录已上传分片索引失败: uploadId={}, chunkIndex={}: {}",
                    uploadId, chunkIndex, e.getMessage());
        }
        log.debug("[MultipartUpload] 分片上传成功: uploadId={}, chunk={}/{}",
                uploadId, chunkIndex + 1, meta.getTotalChunks());
        return true;
    }

    @Override
    public String completeMultipartUpload(String uploadId) {
        ChunkMeta meta = loadMeta(uploadId);
        if (meta == null) {
            log.warn("[MultipartUpload] 分片元数据不存在: uploadId={}", uploadId);
            return null;
        }
        Long uploaded = countUploadedChunks(uploadId);
        if (uploaded == null || uploaded < meta.getTotalChunks()) {
            log.warn("[MultipartUpload] 分片不完整，无法合并: uploadId={}, uploaded={}, total={}",
                    uploadId, uploaded, meta.getTotalChunks());
            return null;
        }
        try {
            // 按序拉取各分片并合并
            int totalLength = 0;
            byte[][] parts = new byte[meta.getTotalChunks()][];
            for (int i = 0; i < meta.getTotalChunks(); i++) {
                try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(bucket())
                        .object(tempChunkKey(uploadId, i))
                        .build())) {
                    parts[i] = readAllBytes(in);
                    totalLength += parts[i].length;
                }
            }
            byte[] merged = new byte[totalLength];
            int offset = 0;
            for (byte[] part : parts) {
                System.arraycopy(part, 0, merged, offset, part.length);
                offset += part.length;
            }
            String fileKey = uploadId + "/" + meta.getFilename();
            // 合并后上传到 MinIO
            try (InputStream in = new ByteArrayInputStream(merged)) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket())
                        .object(fileKey)
                        .stream(in, merged.length, -1)
                        .contentType("application/octet-stream")
                        .build());
            }
            log.info("[MultipartUpload] 分片合并完成: uploadId={}, fileKey={}, size={}",
                    uploadId, fileKey, totalLength);
            cleanup(uploadId, meta.getTotalChunks());
            return fileKey;
        } catch (Exception e) {
            log.warn("[MultipartUpload] 合并分片失败: uploadId={}: {}", uploadId, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void abortMultipartUpload(String uploadId) {
        ChunkMeta meta = loadMeta(uploadId);
        cleanup(uploadId, meta == null ? 0 : meta.getTotalChunks());
        log.info("[MultipartUpload] 取消分片上传: uploadId={}", uploadId);
    }

    @Override
    public String generatePreviewUrl(String fileKey) {
        String presigned = generateMinioPresignedUrl(fileKey);
        if (StringUtils.hasText(kkFileViewUrl)) {
            // kkFileView 标准接入：url 参数为 Base64 编码的可访问文件地址
            String encoded = Base64.getUrlEncoder().encodeToString(presigned.getBytes(StandardCharsets.UTF_8));
            String base = kkFileViewUrl.endsWith("/") ? kkFileViewUrl.substring(0, kkFileViewUrl.length() - 1) : kkFileViewUrl;
            String url = base + "/onlinePreview?url=" + encoded;
            log.debug("[FilePreview] 生成 kkFileView 预览URL: fileKey={}", fileKey);
            return url;
        }
        log.debug("[FilePreview] 生成 MinIO 预签名预览URL: fileKey={}", fileKey);
        return presigned;
    }

    // ==================== 病毒扫描 ====================

    /**
     * 通过 ClamAV daemon（clamd INSTREAM 协议）扫描文件字节流。
     *
     * <p>连接失败时 fail-open 放行（记录告警），避免文件服务强依赖 ClamAV 可用性。
     *
     * @param data     文件字节流
     * @param filename 文件名（仅用于日志）
     * @return true 表示文件安全，false 表示检测到病毒
     */
    private boolean scanWithClamAv(byte[] data, String filename) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(clamavHost, clamavPort), CLAMAV_CONNECT_TIMEOUT);
            socket.setSoTimeout(CLAMAV_READ_TIMEOUT);
            try (OutputStream out = socket.getOutputStream(); InputStream in = socket.getInputStream()) {
                // 发送 INSTREAM 握手指令（z 前缀 = 指令以 \0 结束）
                out.write(CLAMAV_INSTREAM_CMD.getBytes(StandardCharsets.US_ASCII));
                out.flush();
                // 分片发送文件数据：[4 字节大端长度][数据]
                int offset = 0;
                while (offset < data.length) {
                    int len = Math.min(CLAMAV_CHUNK_SIZE, data.length - offset);
                    out.write(intToBytes(len));
                    out.write(data, offset, len);
                    offset += len;
                }
                // 发送 0 长度分片表示数据结束
                out.write(new byte[4]);
                out.flush();
                // 读取扫描结果
                String response = readResponse(in);
                log.debug("[FileVirusScan] 文件 {} 大小 {} 字节，ClamAV 响应: {}",
                        filename, data.length, response);
                if (response.contains("FOUND")) {
                    log.warn("[FileVirusScan] 检测到病毒: file={}, response={}", filename, response);
                    return false;
                }
                return true;
            }
        } catch (Exception e) {
            // 连接失败 fail-open，避免阻塞业务上传
            log.warn("[FileVirusScan] ClamAV 连接失败，fail-open 放行: host={}:{}, reason={}",
                    clamavHost, clamavPort, e.getMessage());
            return true;
        }
    }

    /**
     * 读取 clamd 响应直至连接关闭。
     */
    private String readResponse(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
        int n;
        while ((n = in.read(buf)) != -1) {
            buffer.write(buf, 0, n);
        }
        return buffer.toString(StandardCharsets.US_ASCII.name());
    }

    /**
     * int 转 4 字节大端序。
     */
    private static byte[] intToBytes(int value) {
        return new byte[]{
                (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        };
    }

    // ==================== MinIO / Redis 辅助 ====================

    /**
     * 生成 MinIO 预签名下载 URL（30 分钟过期），失败时降级为本地预览路径。
     */
    private String generateMinioPresignedUrl(String fileKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket())
                    .object(fileKey)
                    .expiry(PREVIEW_EXPIRE_MINUTES, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            log.warn("[FilePreview] 生成 MinIO 预签名 URL 失败: fileKey={}, reason={}", fileKey, e.getMessage());
            return "/api/v1/file/preview/" + fileKey;
        }
    }

    /**
     * 清理分片临时对象与 Redis 元数据。
     */
    private void cleanup(String uploadId, int totalChunks) {
        for (int i = 0; i < totalChunks; i++) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket())
                        .object(tempChunkKey(uploadId, i))
                        .build());
            } catch (Exception e) {
                log.debug("[MultipartUpload] 清理临时分片失败: uploadId={}, chunk={}: {}",
                        uploadId, i, e.getMessage());
            }
        }
        try {
            stringRedisTemplate.delete(metaKey(uploadId));
            stringRedisTemplate.delete(chunksKey(uploadId));
        } catch (Exception e) {
            log.debug("[MultipartUpload] 清理 Redis 元数据失败: uploadId={}: {}", uploadId, e.getMessage());
        }
    }

    private ChunkMeta loadMeta(String uploadId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(metaKey(uploadId));
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return JSON.parseObject(json, ChunkMeta.class);
        } catch (Exception e) {
            log.warn("[MultipartUpload] 读取分片元数据失败: uploadId={}: {}", uploadId, e.getMessage());
            return null;
        }
    }

    private Long countUploadedChunks(String uploadId) {
        try {
            return stringRedisTemplate.opsForSet().size(chunksKey(uploadId));
        } catch (Exception e) {
            log.warn("[MultipartUpload] 读取已上传分片数失败: uploadId={}: {}", uploadId, e.getMessage());
            return null;
        }
    }

    private String bucket() {
        return minioConfig.getDefaultBucket();
    }

    private static String metaKey(String uploadId) {
        return META_KEY_PREFIX + uploadId;
    }

    private static String chunksKey(String uploadId) {
        return CHUNKS_KEY_PREFIX + uploadId;
    }

    private static String tempChunkKey(String uploadId, int chunkIndex) {
        return MULTIPART_PREFIX + uploadId + "/" + chunkIndex;
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            buffer.write(buf, 0, n);
        }
        return buffer.toByteArray();
    }

    /**
     * 分片上传元数据（Redis JSON 存储）。
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ChunkMeta {
        private String filename;
        private long totalSize;
        private int totalChunks;
    }
}
