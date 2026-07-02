package com.njydsz.pmis.file.service.impl;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.file.config.MinioConfig;
import com.njydsz.pmis.file.service.FileEnhanceService;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FileEnhanceServiceImpl 单元测试。
 *
 * <p>覆盖 ClamAV 病毒扫描（fail-open）、Redis+MinIO 分片上传合并、kkFileView/MinIO 预览 URL。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("FileEnhanceServiceImpl 文件增强服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileEnhanceServiceImplTest {

    @Mock
    private MinioClient minioClient;
    @Mock
    private MinioConfig minioConfig;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;

    private FileEnhanceService service;

    @BeforeEach
    void setUp() throws Exception {
        when(minioConfig.getDefaultBucket()).thenReturn("test-bucket");
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOps);

        service = new FileEnhanceServiceImpl(minioClient, minioConfig, stringRedisTemplate);
        // 注入 @Value 属性：ClamAV 指向不可达端口以触发 fail-open
        ReflectionTestUtils.setField(service, "clamavHost", "127.0.0.1");
        ReflectionTestUtils.setField(service, "clamavPort", 1);
        ReflectionTestUtils.setField(service, "kkFileViewUrl", "");
    }

    @Test
    @DisplayName("validateFileType 应接受白名单内的扩展名")
    void validateFileType_shouldAcceptAllowedExtensions() {
        assertThat(service.validateFileType("test.pdf", "application/pdf")).isTrue();
        assertThat(service.validateFileType("test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).isTrue();
        assertThat(service.validateFileType("test.png", "image/png")).isTrue();
    }

    @Test
    @DisplayName("validateFileType 应拒绝白名单外的扩展名")
    void validateFileType_shouldRejectDisallowedExtensions() {
        assertThat(service.validateFileType("test.exe", "application/octet-stream")).isFalse();
        assertThat(service.validateFileType("test.sh", "text/x-sh")).isFalse();
    }

    @Test
    @DisplayName("validateFileType 应拒绝空文件名")
    void validateFileType_shouldRejectNullFilename() {
        assertThat(service.validateFileType(null, "application/pdf")).isFalse();
        assertThat(service.validateFileType("", "application/pdf")).isFalse();
    }

    @Test
    @DisplayName("validateFileSize 应接受合法大小")
    void validateFileSize_shouldAcceptValidSize() {
        assertThat(service.validateFileSize(1024, 104857600)).isTrue();
    }

    @Test
    @DisplayName("validateFileSize 应拒绝零或负数")
    void validateFileSize_shouldRejectZeroOrNegative() {
        assertThat(service.validateFileSize(0, 104857600)).isFalse();
        assertThat(service.validateFileSize(-1, 104857600)).isFalse();
    }

    @Test
    @DisplayName("validateFileSize 应拒绝超过最大限制的大小")
    void validateFileSize_shouldRejectExceedingMaxSize() {
        assertThat(service.validateFileSize(200000000, 104857600)).isFalse();
    }

    @Test
    @DisplayName("scanVirus 在 ClamAV 不可达时应 fail-open 返回 true")
    void scanVirus_shouldFailOpenWhenClamAvUnreachable() {
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());
        // ClamAV 端口 1 不可达，应 fail-open 放行
        assertThat(service.scanVirus(file)).isTrue();
    }

    @Test
    @DisplayName("initMultipartUpload 应返回非空 uploadId 并写入 Redis 元数据")
    void initMultipartUpload_shouldReturnUploadIdAndStoreMeta() {
        String uploadId = service.initMultipartUpload("test.zip", 1024 * 1024 * 100, 10);
        assertThat(uploadId).isNotBlank();
        verify(valueOps).set(eq("multipart:meta:" + uploadId), anyString());
    }

    @Test
    @DisplayName("uploadChunk 应将分片上传到 MinIO 并记录索引")
    void uploadChunk_shouldStoreChunkDataToMinio() throws Exception {
        String uploadId = service.initMultipartUpload("test.zip", 10, 2);
        when(valueOps.get("multipart:meta:" + uploadId))
                .thenReturn(JSON.toJSONString(new FileEnhanceServiceImpl.ChunkMeta("test.zip", 10L, 2)));

        boolean result = service.uploadChunk(uploadId, 0, "chunk0".getBytes());

        assertThat(result).isTrue();
        verify(minioClient).putObject(any(PutObjectArgs.class));
        verify(setOps).add(eq("multipart:chunks:" + uploadId), eq("0"));
    }

    @Test
    @DisplayName("uploadChunk 应拒绝无效的 uploadId")
    void uploadChunk_shouldRejectInvalidUploadId() {
        when(valueOps.get(anyString())).thenReturn(null);
        boolean result = service.uploadChunk("invalid-id", 0, "data".getBytes());
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("uploadChunk 应拒绝无效的分片序号")
    void uploadChunk_shouldRejectInvalidChunkIndex() {
        String uploadId = "u1";
        when(valueOps.get("multipart:meta:" + uploadId))
                .thenReturn(JSON.toJSONString(new FileEnhanceServiceImpl.ChunkMeta("test.zip", 10L, 2)));
        assertThat(service.uploadChunk(uploadId, -1, "data".getBytes())).isFalse();
        assertThat(service.uploadChunk(uploadId, 99, "data".getBytes())).isFalse();
    }

    @Test
    @DisplayName("completeMultipartUpload 在所有分片到齐时应合并并上传 MinIO 返回 fileKey")
    void completeMultipartUpload_shouldReturnFileKeyWhenAllChunksPresent() throws Exception {
        String uploadId = "u-complete";
        when(valueOps.get("multipart:meta:" + uploadId))
                .thenReturn(JSON.toJSONString(new FileEnhanceServiceImpl.ChunkMeta("test.zip", 12L, 2)));
        when(setOps.size("multipart:chunks:" + uploadId)).thenReturn(2L);
        // 每次拉取分片返回新的输入流
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenAnswer(inv -> new ByteArrayInputStream("chunk0".getBytes()))
                .thenAnswer(inv -> new ByteArrayInputStream("chunk1".getBytes()));

        String fileKey = service.completeMultipartUpload(uploadId);

        assertThat(fileKey).contains("test.zip");
        verify(minioClient, atLeastOnce()).putObject(any(PutObjectArgs.class));
        verify(minioClient, atLeastOnce()).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("completeMultipartUpload 在分片缺失时应返回 null")
    void completeMultipartUpload_shouldReturnNullWhenChunksMissing() {
        String uploadId = "u-missing";
        when(valueOps.get("multipart:meta:" + uploadId))
                .thenReturn(JSON.toJSONString(new FileEnhanceServiceImpl.ChunkMeta("test.zip", 10L, 2)));
        when(setOps.size("multipart:chunks:" + uploadId)).thenReturn(1L);

        String fileKey = service.completeMultipartUpload(uploadId);
        assertThat(fileKey).isNull();
    }

    @Test
    @DisplayName("abortMultipartUpload 应清理临时分片与 Redis 元数据")
    void abortMultipartUpload_shouldCleanUpChunks() {
        String uploadId = "u-abort";
        when(valueOps.get("multipart:meta:" + uploadId))
                .thenReturn(JSON.toJSONString(new FileEnhanceServiceImpl.ChunkMeta("test.zip", 10L, 2)));

        service.abortMultipartUpload(uploadId);

        verify(stringRedisTemplate).delete("multipart:meta:" + uploadId);
        verify(stringRedisTemplate).delete("multipart:chunks:" + uploadId);
    }

    @Test
    @DisplayName("generatePreviewUrl 未配置 kkFileView 时应返回 MinIO 预签名 URL")
    void generatePreviewUrl_shouldReturnMinioPresignedUrl() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio.local/test-bucket/file.pdf?token=abc");

        String url = service.generatePreviewUrl("upload1/file.pdf");
        assertThat(url).startsWith("https://minio.local/");
        assertThat(url).contains("file.pdf");
    }

    @Test
    @DisplayName("generatePreviewUrl 配置 kkFileView 时应返回 kkFileView 在线预览地址")
    void generatePreviewUrl_shouldReturnKkFileViewUrlWhenConfigured() throws Exception {
        ReflectionTestUtils.setField(service, "kkFileViewUrl", "http://kkfileview:8012");
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio.local/test-bucket/file.pdf?token=abc");

        String url = service.generatePreviewUrl("upload1/file.pdf");
        assertThat(url).startsWith("http://kkfileview:8012/onlinePreview?url=");
        // url 参数应为 Base64 编码的 MinIO 预签名地址
        String encoded = url.substring(url.indexOf("url=") + 4);
        String decoded = new String(Base64.getUrlDecoder().decode(encoded));
        assertThat(decoded).startsWith("https://minio.local/");
    }

    @Test
    @DisplayName("generatePreviewUrl 在 MinIO 异常时应降级返回本地预览路径")
    void generatePreviewUrl_shouldFallbackWhenMinioFails() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new RuntimeException("minio down"));

        String url = service.generatePreviewUrl("upload1/file.pdf");
        assertThat(url).contains("/preview/");
    }
}
