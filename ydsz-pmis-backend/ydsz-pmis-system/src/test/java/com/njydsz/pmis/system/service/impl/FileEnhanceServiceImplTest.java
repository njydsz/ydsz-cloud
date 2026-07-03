package com.njydsz.pmis.system.service.impl;

import com.njydsz.pmis.system.config.MinioConfig;
import io.minio.*;
import io.minio.http.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileEnhanceServiceImpl 单元测试")
class FileEnhanceServiceImplTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private MinioConfig minioConfig;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private FileEnhanceServiceImpl fileEnhanceService;

    @Nested
    @DisplayName("validateFileType 方法")
    class ValidateFileTypeTest {

        @Test
        @DisplayName("允许的文件扩展名应返回 true")
        void shouldAllowValidExtension() {
            assertThat(fileEnhanceService.validateFileType("test.pdf", "application/pdf")).isTrue();
        }

        @Test
        @DisplayName("不允许的文件扩展名应返回 false")
        void shouldRejectInvalidExtension() {
            assertThat(fileEnhanceService.validateFileType("test.exe", "application/octet-stream")).isFalse();
        }

        @Test
        @DisplayName("空文件名应返回 false")
        void shouldReturnFalseForEmptyFilename() {
            assertThat(fileEnhanceService.validateFileType("", "text/plain")).isFalse();
        }

        @Test
        @DisplayName("文件名为 null 应返回 false")
        void shouldReturnFalseForNullFilename() {
            assertThat(fileEnhanceService.validateFileType(null, "text/plain")).isFalse();
        }
    }

    @Nested
    @DisplayName("validateFileSize 方法")
    class ValidateFileSizeTest {

        @Test
        @DisplayName("文件大小在限制内应返回 true")
        void shouldReturnTrueWhenSizeWithinLimit() {
            assertThat(fileEnhanceService.validateFileSize(1024, 2048)).isTrue();
        }

        @Test
        @DisplayName("文件大小超过限制应返回 false")
        void shouldReturnFalseWhenSizeExceedsLimit() {
            assertThat(fileEnhanceService.validateFileSize(5000, 2048)).isFalse();
        }

        @Test
        @DisplayName("文件大小为 0 应返回 false")
        void shouldReturnFalseForZeroSize() {
            assertThat(fileEnhanceService.validateFileSize(0, 2048)).isFalse();
        }

        @Test
        @DisplayName("文件大小为负数应返回 false")
        void shouldReturnFalseForNegativeSize() {
            assertThat(fileEnhanceService.validateFileSize(-1, 2048)).isFalse();
        }
    }

    @Nested
    @DisplayName("initMultipartUpload 方法")
    class InitMultipartUploadTest {

        @Test
        @DisplayName("初始化分片上传应返回 uploadId")
        void shouldReturnUploadId() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

            String uploadId = fileEnhanceService.initMultipartUpload("test.pdf", 1024, 3);

            assertThat(uploadId).isNotNull();
            assertThat(uploadId).isNotEmpty();
            verify(valueOperations).set(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("generatePreviewUrl 方法")
    class GeneratePreviewUrlTest {

        @Test
        @DisplayName("生成预览 URL 应返回 MinIO 预签名 URL")
        void shouldGeneratePreviewUrl() throws Exception {
            when(minioConfig.getDefaultBucket()).thenReturn("test-bucket");
            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                    .thenReturn("http://minio.example.com/test-bucket/file-key");

            String url = fileEnhanceService.generatePreviewUrl("file-key");

            assertThat(url).isNotNull();
            assertThat(url).contains("minio");
        }

        @Test
        @DisplayName("MinIO 预签名失败时应返回降级路径")
        void shouldReturnFallbackUrlOnFailure() throws Exception {
            when(minioConfig.getDefaultBucket()).thenReturn("test-bucket");
            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                    .thenThrow(new RuntimeException("MinIO error"));

            String url = fileEnhanceService.generatePreviewUrl("file-key");

            assertThat(url).isNotNull();
            assertThat(url).startsWith("/api/");
        }
    }
}