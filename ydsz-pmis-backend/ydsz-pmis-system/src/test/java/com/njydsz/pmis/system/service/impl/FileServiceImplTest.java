package com.njydsz.pmis.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.system.config.MinioConfig;
import com.njydsz.pmis.system.dto.FileUploadDTO;
import com.njydsz.pmis.system.entity.FileDO;
import com.njydsz.pmis.system.mapper.FileMapper;
import io.minio.*;
import io.minio.http.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileServiceImpl 单元测试")
class FileServiceImplTest {

    @Mock
    private FileMapper fileMapper;

    @Mock
    private MinioClient minioClient;

    @Mock
    private MinioConfig minioConfig;

    @InjectMocks
    private FileServiceImpl fileService;

    @Nested
    @DisplayName("upload 方法")
    class UploadTest {

        @Test
        @DisplayName("上传文件成功时应返回 FileDO")
        void shouldUploadFileSuccessfully() throws Exception {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getOriginalFilename()).thenReturn("test.txt");
            when(file.getBytes()).thenReturn("hello".getBytes());
            when(file.getContentType()).thenReturn("text/plain");

            when(minioConfig.getDefaultBucket()).thenReturn("test-bucket");
            when(minioConfig.getUrlExpireSeconds()).thenReturn(3600);
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
            when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(io.minio.ObjectWriteResponse.class));
            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                    .thenReturn("http://minio.example.com/test-bucket/test-key");
            doAnswer(invocation -> {
                FileDO entity = invocation.getArgument(0);
                entity.setId(1L);
                return 1;
            }).when(fileMapper).insert(any(FileDO.class));

            FileUploadDTO dto = new FileUploadDTO();
            dto.setBizType("TEST");
            dto.setBizId("123");

            FileDO result = fileService.upload(file, dto);

            assertThat(result).isNotNull();
            assertThat(result.getOriginalName()).isEqualTo("test.txt");
            verify(fileMapper).insert(any(FileDO.class));
        }

        @Test
        @DisplayName("文件为空时应抛出异常")
        void shouldThrowWhenFileIsNull() {
            FileUploadDTO dto = new FileUploadDTO();

            assertThatThrownBy(() -> fileService.upload(null, dto))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("文件不能为空");
        }
    }

    @Nested
    @DisplayName("delete 方法")
    class DeleteTest {

        @Test
        @DisplayName("删除文件成功时应调用 mapper.deleteById")
        void shouldDeleteFileSuccessfully() throws Exception {
            FileDO file = new FileDO();
            file.setId(1L);
            file.setBucket("test-bucket");
            file.setFilePath("2024/01/test.txt");
            when(fileMapper.selectById(1L)).thenReturn(file);
            doNothing().when(minioClient).removeObject(any(RemoveObjectArgs.class));
            when(fileMapper.deleteById(1L)).thenReturn(1);

            assertThatCode(() -> fileService.delete(1L)).doesNotThrowAnyException();
            verify(fileMapper).deleteById(1L);
        }

        @Test
        @DisplayName("文件不存在时应抛出异常")
        void shouldThrowWhenFileNotFound() {
            when(fileMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> fileService.delete(999L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("文件不存在");
        }
    }

    @Nested
    @DisplayName("getById 方法")
    class GetByIdTest {

        @Test
        @DisplayName("文件存在时应返回 FileDO")
        void shouldReturnFileWhenExists() {
            FileDO file = new FileDO();
            file.setId(1L);
            file.setOriginalName("test.txt");
            when(fileMapper.selectById(1L)).thenReturn(file);

            FileDO result = fileService.getById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getOriginalName()).isEqualTo("test.txt");
        }

        @Test
        @DisplayName("文件不存在时应抛出异常")
        void shouldThrowWhenNotFound() {
            when(fileMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> fileService.getById(999L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("文件不存在");
        }
    }

    @Nested
    @DisplayName("download 方法")
    class DownloadTest {

        @Test
        @DisplayName("下载文件成功时应返回 InputStream")
        void shouldDownloadFileSuccessfully() throws Exception {
            FileDO file = new FileDO();
            file.setId(1L);
            file.setBucket("test-bucket");
            file.setFilePath("path/to/file.txt");
            when(fileMapper.selectById(1L)).thenReturn(file);
            when(minioClient.getObject(any(GetObjectArgs.class)))
                    .thenReturn(mock(io.minio.GetObjectResponse.class));

            InputStream result = fileService.download(1L);

            assertThat(result).isNotNull();
            verify(minioClient).getObject(any(GetObjectArgs.class));
        }
    }

    @Test
    @DisplayName("分页查询应返回正确结果")
    void shouldReturnPagedFiles() {
        when(fileMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

        Page<FileDO> result = fileService.page(1, 10, "TEST", null, null);

        assertThat(result).isNotNull();
        verify(fileMapper).selectPage(any(Page.class), any());
    }
}