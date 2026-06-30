package com.njydsz.pmis.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.file.config.MinioConfig;
import com.njydsz.pmis.file.dto.FileUploadDTO;
import com.njydsz.pmis.file.entity.FileDO;
import com.njydsz.pmis.file.mapper.FileMapper;
import com.njydsz.pmis.file.service.FileService;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 文件存储服务实现（MinIO）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final FileMapper fileMapper;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileDO upload(MultipartFile file, FileUploadDTO dto) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "文件不能为空");
        }
        return uploadInternal(
                file.getOriginalFilename(),
                file.getBytes(),
                file.getContentType(),
                dto
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileDO uploadBytes(String originalName, byte[] content, String contentType, FileUploadDTO dto) throws Exception {
        if (content == null || content.length == 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "文件内容不能为空");
        }
        return uploadInternal(originalName, content, contentType, dto);
    }

    private FileDO uploadInternal(String originalName, byte[] content, String contentType,
                                  FileUploadDTO dto) throws Exception {
        String bucket = StringUtils.hasText(dto.getBucket()) ? dto.getBucket() : minioConfig.getDefaultBucket();
        // 确保 bucket 存在
        ensureBucket(bucket);

        // 计算 SHA-256
        String hash = sha256Hex(content);

        // 生成对象 key：yyyyMM/dd/uuid-原始文件名
        LocalDateTime now = LocalDateTime.now();
        String key = String.format("%04d%02d/%02d/%s-%s",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                sanitizeName(originalName));

        // 上传
        try (InputStream in = new java.io.ByteArrayInputStream(content)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(in, content.length, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        }

        // 生成预签名 URL
        int expire = minioConfig.getUrlExpireSeconds() == null ? 3600 : minioConfig.getUrlExpireSeconds();
        String url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucket)
                .object(key)
                .expiry(expire)
                .build());

        FileDO entity = new FileDO();
        entity.setFileName(key);
        entity.setOriginalName(originalName);
        entity.setFilePath(key);
        entity.setBucket(bucket);
        entity.setContentType(contentType);
        entity.setFileSize((long) content.length);
        entity.setFileHash(hash);
        entity.setBizType(dto.getBizType());
        entity.setBizId(dto.getBizId());
        entity.setStorageType("MINIO");
        entity.setAccessUrl(url);
        entity.setUrlExpireAt(LocalDateTime.now().plusSeconds(expire));
        entity.setUploaderId(dto.getUploaderId());
        entity.setUploaderName(dto.getUploaderName());
        entity.setTenantId(1L);
        entity.setDescription(dto.getDescription());
        fileMapper.insert(entity);

        log.info("[File] 上传成功: id={} name={} size={} bucket={} key={}",
                entity.getId(), originalName, content.length, bucket, key);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) throws Exception {
        FileDO f = fileMapper.selectById(id);
        if (f == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "文件不存在");
        }
        deleteFromMinio(f);
        fileMapper.deleteById(id);
        log.info("[File] 删除: id={} key={}", id, f.getFilePath());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<Long> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            try {
                delete(id);
            } catch (Exception e) {
                log.warn("[File] 批量删除失败: id={} reason={}", id, e.getMessage());
            }
        }
    }

    @Override
    public FileDO getById(Long id) {
        FileDO f = fileMapper.selectById(id);
        if (f == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "文件不存在");
        }
        return f;
    }

    @Override
    public String getPresignedUrl(Long id, Integer expireSeconds) {
        FileDO f = getById(id);
        int expire = expireSeconds == null ? minioConfig.getUrlExpireSeconds() : expireSeconds;
        try {
            String url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(f.getBucket())
                    .object(f.getFilePath())
                    .expiry(expire)
                    .build());
            // 回写数据库
            f.setAccessUrl(url);
            f.setUrlExpireAt(LocalDateTime.now().plusSeconds(expire));
            fileMapper.updateById(f);
            return url;
        } catch (Exception e) {
            log.error("[File] 生成预签名 URL 失败: {}", e.getMessage(), e);
            throw new BizException(BizErrorCode.INTERNAL_ERROR, "生成预签名 URL 失败: " + e.getMessage());
        }
    }

    @Override
    public InputStream download(Long id) throws Exception {
        FileDO f = getById(id);
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(f.getBucket())
                .object(f.getFilePath())
                .build());
    }

    @Override
    public List<FileDO> listByBiz(String bizType, String bizId) {
        return fileMapper.selectByBiz(bizType, bizId);
    }

    @Override
    public Page<FileDO> page(int page, int size, String bizType, String bizId, String keyword) {
        Page<FileDO> p = new Page<>(page, size);
        LambdaQueryWrapper<FileDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(bizType)) {
            w.eq(FileDO::getBizType, bizType);
        }
        if (StringUtils.hasText(bizId)) {
            w.eq(FileDO::getBizId, bizId);
        }
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(FileDO::getOriginalName, keyword)
                    .or().like(FileDO::getFileName, keyword));
        }
        w.orderByDesc(FileDO::getCreatedAt);
        return fileMapper.selectPage(p, w);
    }

    // ==================== 私有方法 ====================

    private void ensureBucket(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("[File] 创建 bucket: {}", bucket);
        }
    }

    private void deleteFromMinio(FileDO f) throws Exception {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(f.getBucket())
                    .object(f.getFilePath())
                    .build());
        } catch (Exception e) {
            log.warn("[File] 从 MinIO 删除失败: key={} reason={}", f.getFilePath(), e.getMessage());
        }
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return DigestUtils.md5DigestAsHex(content);
        }
    }

    private String sanitizeName(String name) {
        if (!StringUtils.hasText(name)) {
            return "file";
        }
        // 简单清洗：保留 ASCII 字母数字、中文、点、横线、下划线
        return name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
    }
}
