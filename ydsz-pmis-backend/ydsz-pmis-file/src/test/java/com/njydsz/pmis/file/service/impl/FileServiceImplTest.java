package com.njydsz.pmis.file.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.file.config.MinioConfig;
import com.njydsz.pmis.file.dto.FileUploadDTO;
import com.njydsz.pmis.file.entity.FileDO;
import com.njydsz.pmis.file.mapper.FileMapper;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FileServiceImpl 单元测试
 */
@DisplayName("FileServiceImpl 文件存储测试")
class FileServiceImplTest {

    private FileMapper fileMapper;
    private MinioClient minioClient;
    private MinioConfig minioConfig;
    private FileServiceImpl service;

    @BeforeEach
    void setUp() {
        fileMapper = mock(FileMapper.class);
        minioClient = mock(MinioClient.class);
        minioConfig = new MinioConfig();
        minioConfig.setEndpoint("http://127.0.0.1:9000");
        minioConfig.setAccessKey("ak");
        minioConfig.setSecretKey("sk");
        minioConfig.setDefaultBucket("pmis");
        minioConfig.setUrlExpireSeconds(3600);
        service = new FileServiceImpl(fileMapper, minioClient, minioConfig);
    }

    @Test
    @DisplayName("upload 空文件应抛 BAD_REQUEST")
    void upload_empty() {
        MockMultipartFile empty = new MockMultipartFile("file", "x.txt", "text/plain", new byte[0]);
        assertThatThrownBy(() -> service.upload(empty, new FileUploadDTO()))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("upload 正常文件应写入元信息并存储")
    void upload_ok() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://example.com/presigned");
        when(fileMapper.insert(any(FileDO.class))).thenAnswer(inv -> {
            ((FileDO) inv.getArgument(0)).setId(1L);
            return 1;
        });

        MockMultipartFile mf = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        FileUploadDTO dto = new FileUploadDTO();
        dto.setBizType("CONTRACT");
        dto.setBizId("B-1");
        dto.setUploaderId(10L);
        dto.setUploaderName("张三");

        FileDO f = service.upload(mf, dto);

        assertThat(f.getId()).isEqualTo(1L);
        assertThat(f.getBucket()).isEqualTo("pmis");
        assertThat(f.getFileSize()).isEqualTo(5L);
        assertThat(f.getFileHash()).isNotBlank();
        assertThat(f.getOriginalName()).isEqualTo("a.txt");
        assertThat(f.getStorageType()).isEqualTo("MINIO");

        // 验证 putObject 被调用
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("delete 不存在的 ID 应抛 NOT_FOUND")
    void delete_notFound() {
        when(fileMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("delete 正常路径应删除 MinIO 对象与元信息")
    void delete_ok() throws Exception {
        FileDO f = new FileDO();
        f.setId(1L);
        f.setBucket("pmis");
        f.setFilePath("202601/01/abc-test.txt");
        when(fileMapper.selectById(1L)).thenReturn(f);

        service.delete(1L);

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        verify(fileMapper).deleteById(1L);
    }

    @Test
    @DisplayName("getPresignedUrl 应返回新签名")
    void presigned() throws Exception {
        FileDO f = new FileDO();
        f.setId(1L);
        f.setBucket("pmis");
        f.setFilePath("k1");
        when(fileMapper.selectById(1L)).thenReturn(f);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://signed/url");

        String url = service.getPresignedUrl(1L, 60);
        assertThat(url).isEqualTo("http://signed/url");
    }

    @Test
    @DisplayName("listByBiz / page 应走 Mapper")
    void listByBiz() {
        when(fileMapper.selectByBiz("T", "1")).thenReturn(List.of(new FileDO()));
        assertThat(service.listByBiz("T", "1")).hasSize(1);
    }

    @Test
    @DisplayName("uploadBytes 应能跳过 HTTP Multipart 直接上传")
    void uploadBytes() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://u/");
        when(fileMapper.insert(any(FileDO.class))).thenAnswer(inv -> {
            ((FileDO) inv.getArgument(0)).setId(7L);
            return 1;
        });
        FileDO f = service.uploadBytes("x.bin", "abc".getBytes(), "application/octet-stream", new FileUploadDTO());
        assertThat(f.getId()).isEqualTo(7L);
        assertThat(f.getFileSize()).isEqualTo(3L);
    }
}
