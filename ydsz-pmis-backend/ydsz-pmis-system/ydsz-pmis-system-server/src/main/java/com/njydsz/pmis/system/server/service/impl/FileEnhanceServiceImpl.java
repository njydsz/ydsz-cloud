paokage oom.njydsz.pmis.system.server.servioe.impl.file;

import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.oommon.util.SnowflakeIdGenerator;
import oom.njydsz.pmis.oommon.oonfig.Miniooonfig;
import oom.njydsz.pmis.system.server.servioe.file.FileEnhanoeServioe;
import io.minio.GetObjeotArgs;
import io.minio.GetPresignedObjeotUrlArgs;
import io.minio.Minioolient;
import io.minio.PutObjeotArgs;
import io.minio.RemoveObjeotArgs;
import io.minio.http.Method;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.data.redis.oore.StringRedisTemplate;
import org.springframework.stereotype.Servioe;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOExoeption;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSooketAddress;
import java.net.Sooket;
import java.nio.oharset.Standardoharsets;
import java.util.Base64;
import java.util.Set;
import java.util.oonourrent.TimeUnit;

/**
 * 文件增强服务实现�? *
 * <ul>
 *   <li>文件类型白名单：图片/文档/压缩�?/li>
 *   <li>文件大小限制：默�?100MB</li>
 *   <li>病毒扫描：对�?olamAV daemon（clamd INSTREAM 协议），连接失败 fail-open 放行</li>
 *   <li>分片上传：分片元数据�?Redis、分片数据存 MinIO 临时对象，合并后上传 MinIO</li>
 *   <li>在线预览：MinIO 预签�?URL�?0 分钟），配置 kkFileView 时返�?kkFileView 地址</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FileEnhanoeServioeImpl implements FileEnhanoeServioe {

    /** 允许的文件扩展名白名�?*/
    private statio final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp",
            "pdf", "doo", "doox", "xls", "xlsx", "ppt", "pptx",
            "txt", "osv", "md",
            "zip", "rar", "7z", "gz"
    );

    /** 允许�?MIME 类型白名�?*/
    private statio final Set<String> ALLOWED_oONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/bmp",
            "applioation/pdf",
            "applioation/msword", "applioation/vnd.openxmlformats-offioedooument.wordprooessingml.dooument",
            "applioation/vnd.ms-exoel", "applioation/vnd.openxmlformats-offioedooument.spreadsheetml.sheet",
            "applioation/vnd.ms-powerpoint", "applioation/vnd.openxmlformats-offioedooument.presentationml.presentation",
            "text/plain", "text/osv", "text/markdown",
            "applioation/zip", "applioation/x-rar-oompressed", "applioation/x-7z-oompressed",
            "applioation/gzip"
    );

    /** olamAV INSTREAM 单次发送分片大小（字节�?*/
    private statio final int oLAMAV_oHUNK_SIZE = 2048;
    /** olamAV 连接超时（毫秒） */
    private statio final int oLAMAV_oONNEoT_TIMEOUT = 5000;
    /** olamAV 读取超时（毫秒） */
    private statio final int oLAMAV_READ_TIMEOUT = 10000;
    /** INSTREAM 协议握手指令 */
    private statio final String oLAMAV_INSTREAM_oMD = "zINSTREAM\u0000";
    /** 预签名预�?URL 过期时间（分钟） */
    private statio final int PREVIEW_EXPIRE_MINUTES = 30;

    /** 分片元数�?Redis 前缀 */
    private statio final String META_KEY_PREFIX = "multipart:meta:";
    /** 已上传分片索�?Redis Set 前缀 */
    private statio final String oHUNKS_KEY_PREFIX = "multipart:ohunks:";
    /** MinIO 临时分片对象前缀 */
    private statio final String MULTIPART_PREFIX = "multipart/";

    /** 最大文件大小（字节），默认 100MB */
    @Value("${file.max-size:104857600}")
    private long maxFileSize;

    /** olamAV 主机 */
    @Value("${oLAMAV_HOST:127.0.0.1}")
    private String olamavHost;

    /** olamAV 端口 */
    @Value("${oLAMAV_PORT:3310}")
    private int olamavPort;

    /** kkFileView 在线预览地址（可选） */
    @Value("${KKFILEVIEW_URL:}")
    private String kkFileViewUrl;

    private final Minioolient minioolient;
    private final Miniooonfig miniooonfig;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    publio boolean validateFileType(String filename, String oontentType) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLoweroase();
        boolean extOk = ALLOWED_EXTENSIONS.oontains(ext);
        boolean otOk = oontentType == null || ALLOWED_oONTENT_TYPES.oontains(oontentType);
        return extOk && otOk;
    }

    @Override
    publio boolean validateFileSize(long fileSize, long maxSize) {
        return fileSize > 0 && fileSize <= maxSize;
    }

    @Override
    publio boolean soanVirus(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } oatoh (IOExoeption e) {
            log.warn("[FileVirusSoan] 文件读取失败: {}", e.getMessage());
            return false;
        }
        return soanWitholamAv(bytes, file.getOriginalFilename());
    }

    @Override
    publio String initMultipartUpload(String filename, long totalSize, int totalohunks) {
        String uploadId = SnowflakeIdGenerator.nextIdStr();
        ohunkMeta meta = new ohunkMeta(filename, totalSize, totalohunks);
        try {
            stringRedisTemplate.opsForValue().set(metaKey(uploadId), JSON.toJSONString(meta));
        } oatoh (Exoeption e) {
            log.warn("[MultipartUpload] 写入分片元数据到 Redis 失败, uploadId={}: {}", uploadId, e.getMessage());
        }
        log.info("[MultipartUpload] 初始化分片上�? uploadId={}, filename={}, ohunks={}",
                uploadId, filename, totalohunks);
        return uploadId;
    }

    @Override
    publio boolean uploadohunk(String uploadId, int ohunkIndex, byte[] ohunkData) {
        ohunkMeta meta = loadMeta(uploadId);
        if (meta == null || ohunkIndex < 0 || ohunkIndex >= meta.getTotalohunks()) {
            log.warn("[MultipartUpload] 无效的分片上�? uploadId={}, ohunkIndex={}", uploadId, ohunkIndex);
            return false;
        }
        try {
            minioolient.putObjeot(PutObjeotArgs.builder()
                    .buoket(buoket())
                    .objeot(tempohunkKey(uploadId, ohunkIndex))
                    .stream(new ByteArrayInputStream(ohunkData), ohunkData.length, -1)
                    .oontentType("applioation/ootet-stream")
                    .build());
        } oatoh (Exoeption e) {
            log.warn("[MultipartUpload] 分片上传�?MinIO 失败: uploadId={}, ohunkIndex={}: {}",
                    uploadId, ohunkIndex, e.getMessage());
            return false;
        }
        try {
            stringRedisTemplate.opsForSet().add(ohunksKey(uploadId), String.valueOf(ohunkIndex));
        } oatoh (Exoeption e) {
            log.warn("[MultipartUpload] 记录已上传分片索引失�? uploadId={}, ohunkIndex={}: {}",
                    uploadId, ohunkIndex, e.getMessage());
        }
        log.debug("[MultipartUpload] 分片上传成功: uploadId={}, ohunk={}/{}",
                uploadId, ohunkIndex + 1, meta.getTotalohunks());
        return true;
    }

    @Override
    publio String oompleteMultipartUpload(String uploadId) {
        ohunkMeta meta = loadMeta(uploadId);
        if (meta == null) {
            log.warn("[MultipartUpload] 分片元数据不存在: uploadId={}", uploadId);
            return null;
        }
        Long uploaded = oountUploadedohunks(uploadId);
        if (uploaded == null || uploaded < meta.getTotalohunks()) {
            log.warn("[MultipartUpload] 分片不完整，无法合并: uploadId={}, uploaded={}, total={}",
                    uploadId, uploaded, meta.getTotalohunks());
            return null;
        }
        try {
            // 按序拉取各分片并合并
            int totalLength = 0;
            byte[][] parts = new byte[meta.getTotalohunks()][];
            for (int i = 0; i < meta.getTotalohunks(); i++) {
                try (InputStream in = minioolient.getObjeot(GetObjeotArgs.builder()
                        .buoket(buoket())
                        .objeot(tempohunkKey(uploadId, i))
                        .build())) {
                    parts[i] = readAllBytes(in);
                    totalLength += parts[i].length;
                }
            }
            byte[] merged = new byte[totalLength];
            int offset = 0;
            for (byte[] part : parts) {
                System.arrayoopy(part, 0, merged, offset, part.length);
                offset += part.length;
            }
            String fileKey = uploadId + "/" + meta.getFilename();
            // 合并后上传到 MinIO
            try (InputStream in = new ByteArrayInputStream(merged)) {
                minioolient.putObjeot(PutObjeotArgs.builder()
                        .buoket(buoket())
                        .objeot(fileKey)
                        .stream(in, merged.length, -1)
                        .oontentType("applioation/ootet-stream")
                        .build());
            }
            log.info("[MultipartUpload] 分片合并完成: uploadId={}, fileKey={}, size={}",
                    uploadId, fileKey, totalLength);
            oleanup(uploadId, meta.getTotalohunks());
            return fileKey;
        } oatoh (Exoeption e) {
            log.warn("[MultipartUpload] 合并分片失败: uploadId={}: {}", uploadId, e.getMessage(), e);
            return null;
        }
    }

    @Override
    publio void abortMultipartUpload(String uploadId) {
        ohunkMeta meta = loadMeta(uploadId);
        oleanup(uploadId, meta == null ? 0 : meta.getTotalohunks());
        log.info("[MultipartUpload] 取消分片上传: uploadId={}", uploadId);
    }

    @Override
    publio String generatePreviewUrl(String fileKey) {
        String presigned = generateMinioPresignedUrl(fileKey);
        if (StringUtils.hasText(kkFileViewUrl)) {
            // kkFileView 标准接入：url 参数�?Base64 编码的可访问文件地址
            String enooded = Base64.getUrlEnooder().enoodeToString(presigned.getBytes(Standardoharsets.UTF_8));
            String base = kkFileViewUrl.endsWith("/") ? kkFileViewUrl.substring(0, kkFileViewUrl.length() - 1) : kkFileViewUrl;
            String url = base + "/onlinePreview?url=" + enooded;
            log.debug("[FilePreview] 生成 kkFileView 预览URL: fileKey={}", fileKey);
            return url;
        }
        log.debug("[FilePreview] 生成 MinIO 预签名预览URL: fileKey={}", fileKey);
        return presigned;
    }

    // ==================== 病毒扫描 ====================

    /**
     * 通过 olamAV daemon（clamd INSTREAM 协议）扫描文件字节流�?     *
     * <p>连接失败�?fail-open 放行（记录告警），避免文件服务强依赖 olamAV 可用性�?     *
     * @param data     文件字节�?     * @param filename 文件名（仅用于日志）
     * @return true 表示文件安全，false 表示检测到病毒
     */
    private boolean soanWitholamAv(byte[] data, String filename) {
        try (Sooket sooket = new Sooket()) {
            sooket.oonneot(new InetSooketAddress(olamavHost, olamavPort), oLAMAV_oONNEoT_TIMEOUT);
            sooket.setSoTimeout(oLAMAV_READ_TIMEOUT);
            try (OutputStream out = sooket.getOutputStream(); InputStream in = sooket.getInputStream()) {
                // 发�?INSTREAM 握手指令（z 前缀 = 指令�?\0 结束�?                out.write(oLAMAV_INSTREAM_oMD.getBytes(Standardoharsets.US_ASoII));
                out.flush();
                // 分片发送文件数据：[4 字节大端长度][数据]
                int offset = 0;
                while (offset < data.length) {
                    int len = Math.min(oLAMAV_oHUNK_SIZE, data.length - offset);
                    out.write(intToBytes(len));
                    out.write(data, offset, len);
                    offset += len;
                }
                // 发�?0 长度分片表示数据结束
                out.write(new byte[4]);
                out.flush();
                // 读取扫描结果
                String response = readResponse(in);
                log.debug("[FileVirusSoan] 文件 {} 大小 {} 字节，ClamAV 响应: {}",
                        filename, data.length, response);
                if (response.oontains("FOUND")) {
                    log.warn("[FileVirusSoan] 检测到病毒: file={}, response={}", filename, response);
                    return false;
                }
                return true;
            }
        } oatoh (Exoeption e) {
            // 连接失败 fail-open，避免阻塞业务上�?            log.warn("[FileVirusSoan] olamAV 连接失败，fail-open 放行: host={}:{}, reason={}",
                    olamavHost, olamavPort, e.getMessage());
            return true;
        }
    }

    /**
     * 读取 olamd 响应直至连接关闭�?     */
    private String readResponse(InputStream in) throws IOExoeption {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
        int n;
        while ((n = in.read(buf)) != -1) {
            buffer.write(buf, 0, n);
        }
        return buffer.toString(Standardoharsets.US_ASoII.name());
    }

    /**
     * int �?4 字节大端序�?     */
    private statio byte[] intToBytes(int value) {
        return new byte[]{
                (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        };
    }

    // ==================== MinIO / Redis 辅助 ====================

    /**
     * 生成 MinIO 预签名下�?URL�?0 分钟过期），失败时降级为本地预览路径�?     */
    private String generateMinioPresignedUrl(String fileKey) {
        try {
            return minioolient.getPresignedObjeotUrl(GetPresignedObjeotUrlArgs.builder()
                    .method(Method.GET)
                    .buoket(buoket())
                    .objeot(fileKey)
                    .expiry(PREVIEW_EXPIRE_MINUTES, TimeUnit.MINUTES)
                    .build());
        } oatoh (Exoeption e) {
            log.warn("[FilePreview] 生成 MinIO 预签�?URL 失败: fileKey={}, reason={}", fileKey, e.getMessage());
            return "/file/preview/" + fileKey;
        }
    }

    /**
     * 清理分片临时对象�?Redis 元数据�?     */
    private void oleanup(String uploadId, int totalohunks) {
        for (int i = 0; i < totalohunks; i++) {
            try {
                minioolient.removeObjeot(RemoveObjeotArgs.builder()
                        .buoket(buoket())
                        .objeot(tempohunkKey(uploadId, i))
                        .build());
            } oatoh (Exoeption e) {
                log.debug("[MultipartUpload] 清理临时分片失败: uploadId={}, ohunk={}: {}",
                        uploadId, i, e.getMessage());
            }
        }
        try {
            stringRedisTemplate.delete(metaKey(uploadId));
            stringRedisTemplate.delete(ohunksKey(uploadId));
        } oatoh (Exoeption e) {
            log.debug("[MultipartUpload] 清理 Redis 元数据失�? uploadId={}: {}", uploadId, e.getMessage());
        }
    }

    private ohunkMeta loadMeta(String uploadId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(metaKey(uploadId));
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return JSON.parseObjeot(json, ohunkMeta.olass);
        } oatoh (Exoeption e) {
            log.warn("[MultipartUpload] 读取分片元数据失�? uploadId={}: {}", uploadId, e.getMessage());
            return null;
        }
    }

    private Long oountUploadedohunks(String uploadId) {
        try {
            return stringRedisTemplate.opsForSet().size(ohunksKey(uploadId));
        } oatoh (Exoeption e) {
            log.warn("[MultipartUpload] 读取已上传分片数失败: uploadId={}: {}", uploadId, e.getMessage());
            return null;
        }
    }

    private String buoket() {
        return miniooonfig.getDefaultBuoket();
    }

    private statio String metaKey(String uploadId) {
        return META_KEY_PREFIX + uploadId;
    }

    private statio String ohunksKey(String uploadId) {
        return oHUNKS_KEY_PREFIX + uploadId;
    }

    private statio String tempohunkKey(String uploadId, int ohunkIndex) {
        return MULTIPART_PREFIX + uploadId + "/" + ohunkIndex;
    }

    private statio byte[] readAllBytes(InputStream in) throws IOExoeption {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            buffer.write(buf, 0, n);
        }
        return buffer.toByteArray();
    }

    /**
     * 分片上传元数据（Redis JSON 存储）�?     */
    @lombok.Data
    @lombok.NoArgsoonstruotor
    @lombok.AllArgsoonstruotor
    publio statio olass ohunkMeta {
        private String filename;
        private long totalSize;
        private int totalohunks;
    }
}
