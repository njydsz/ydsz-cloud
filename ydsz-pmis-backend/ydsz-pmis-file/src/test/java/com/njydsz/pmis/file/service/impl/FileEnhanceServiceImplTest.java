package com.njydsz.pmis.file.service.impl;

import com.njydsz.pmis.file.service.FileEnhanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FileEnhanceServiceImpl 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("FileEnhanceServiceImpl 文件增强服务测试")
class FileEnhanceServiceImplTest {

    private FileEnhanceService service;

    @BeforeEach
    void setUp() {
        service = new FileEnhanceServiceImpl();
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
        assertThat(service.validateFileType("test.js", "application/javascript")).isFalse();
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
    @DisplayName("scanVirus 对合法文件应返回 true")
    void scanVirus_shouldReturnTrueForValidFile() {
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());
        assertThat(service.scanVirus(file)).isTrue();
    }

    @Test
    @DisplayName("initMultipartUpload 应返回非空 uploadId")
    void initMultipartUpload_shouldReturnUploadId() {
        String uploadId = service.initMultipartUpload("test.zip", 1024 * 1024 * 100, 10);
        assertThat(uploadId).isNotBlank();
    }

    @Test
    @DisplayName("uploadChunk 应存储分片数据")
    void uploadChunk_shouldStoreChunkData() {
        String uploadId = service.initMultipartUpload("test.zip", 10, 2);
        boolean result = service.uploadChunk(uploadId, 0, "chunk0".getBytes());
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("uploadChunk 应拒绝无效的 uploadId")
    void uploadChunk_shouldRejectInvalidUploadId() {
        boolean result = service.uploadChunk("invalid-id", 0, "data".getBytes());
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("uploadChunk 应拒绝无效的分片序号")
    void uploadChunk_shouldRejectInvalidChunkIndex() {
        String uploadId = service.initMultipartUpload("test.zip", 10, 2);
        assertThat(service.uploadChunk(uploadId, -1, "data".getBytes())).isFalse();
        assertThat(service.uploadChunk(uploadId, 99, "data".getBytes())).isFalse();
    }

    @Test
    @DisplayName("completeMultipartUpload 在所有分片到齐时应返回 fileKey")
    void completeMultipartUpload_shouldReturnFileKeyWhenAllChunksPresent() {
        String uploadId = service.initMultipartUpload("test.zip", 12, 2);
        service.uploadChunk(uploadId, 0, "chunk0".getBytes());
        service.uploadChunk(uploadId, 1, "chunk1".getBytes());
        String fileKey = service.completeMultipartUpload(uploadId);
        assertThat(fileKey).contains("test.zip");
    }

    @Test
    @DisplayName("completeMultipartUpload 在分片缺失时应返回 null")
    void completeMultipartUpload_shouldReturnNullWhenChunksMissing() {
        String uploadId = service.initMultipartUpload("test.zip", 10, 2);
        service.uploadChunk(uploadId, 0, "chunk0".getBytes());
        // chunk 1 未上传
        String fileKey = service.completeMultipartUpload(uploadId);
        assertThat(fileKey).isNull();
    }

    @Test
    @DisplayName("abortMultipartUpload 应清理分片缓存")
    void abortMultipartUpload_shouldCleanUpChunks() {
        String uploadId = service.initMultipartUpload("test.zip", 10, 2);
        service.uploadChunk(uploadId, 0, "chunk0".getBytes());
        service.abortMultipartUpload(uploadId);
        // 再次完成应该返回 null
        String fileKey = service.completeMultipartUpload(uploadId);
        assertThat(fileKey).isNull();
    }

    @Test
    @DisplayName("generatePreviewUrl 应返回包含 /preview/ 的路径")
    void generatePreviewUrl_shouldReturnPreviewPath() {
        String url = service.generatePreviewUrl("test/file.pdf");
        assertThat(url).contains("/preview/");
    }
}
