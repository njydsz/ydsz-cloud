package com.njydsz.pmis.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.system.config.MinioConfig;
import com.njydsz.pmis.system.dto.FileUploadDTO;
import com.njydsz.pmis.system.entity.FileDO;
import com.njydsz.pmis.system.mapper.FileMapper;
import com.njydsz.pmis.system.service.FileService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件存储服务实现（MinIO）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    /** 文件 Mapper */
    private final FileMapper fileMapper;
    /** MinIO 客户端 */
    private final MinioClient minioClient;
    /** MinIO 配置 */
    private final MinioConfig minioConfig;

    /**
     * 上传文件
     *
     * @param file 上传的文件
     * @param dto  上传附加参数
     * @return 文件元信息
     * @throws Exception 上传过程中发生异常
     */
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

    /**
     * 上传字节流
     *
     * @param originalName 原始文件名
     * @param content      文件内容
     * @param contentType  MIME 类型
     * @param dto          上传附加参数
     * @return 文件元信息
     * @throws Exception 上传过程中发生异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileDO uploadBytes(String originalName, byte[] content, String contentType, FileUploadDTO dto) throws Exception {
        if (content == null || content.length == 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "文件内容不能为空");
        }
        return uploadInternal(originalName, content, contentType, dto);
    }

    /**
     * 内部上传实现：计算哈希、生成对象 key、上传至 MinIO、写入元信息
     *
     * @param originalName 原始文件名
     * @param content      文件内容
     * @param contentType  MIME 类型
     * @param dto          上传附加参数
     * @return 文件元信息
     * @throws Exception 上传过程中发生异常
     */
    private FileDO uploadInternal(String originalName, byte[] content, String contentType,
                                  FileUploadDTO dto) throws Exception {
        String bucket = StringUtils.hasText(dto.getBucket()) ? dto.getBucket() : minioConfig.getDefaultBucket();
        // 确保 bucket 存在
        ensureBucket(bucket);

        // 计算 SHA-256
        String hash = sha256Hex(content);

        // 生成对象 key：yyyyMM/dd/雪花ID-原始文件名
        LocalDateTime now = LocalDateTime.now();
        String key = String.format("%04d%02d/%02d/%s-%s",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                SnowflakeIdGenerator.nextIdStr().substring(0, 12),
                sanitizeName(originalName));

        // 上传
        try (InputStream in = new ByteArrayInputStream(content)) {
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
        entity.setTenantId(TenantContext.getTenantId());
        entity.setDescription(dto.getDescription());
        fileMapper.insert(entity);

        log.info("[File] 上传成功: id={} name={} size={} bucket={} key={}",
                entity.getId(), originalName, content.length, bucket, key);
        return entity;
    }

    /**
     * 删除文件
     *
     * @param id 文件 ID
     * @throws Exception 删除过程中发生异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) throws Exception {
        FileDO f = fileMapper.selectById(id);
        if (f == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "文件不存在");
        }
        deleteFromMinio(f);
        fileMapper.deleteById(id);
        log.info("[File] 删除: id={} key={}", id, f.getFilePath());
    }

    /**
     * 批量删除文件（单条失败不影响其他文件）
     *
     * @param ids 文件 ID 列表
     * @throws Exception 删除过程中发生异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<Long> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String id : ids) {
            try {
                delete(id);
            } catch (Exception e) {
                log.warn("[File] 批量删除失败: id={} reason={}", id, e.getMessage());
            }
        }
    }

    /**
     * 获取文件元信息
     *
     * @param id 文件 ID
     * @return 文件元信息
     * @throws BizException 当文件不存在时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public FileDO getById(String id) {
        FileDO f = fileMapper.selectById(id);
        if (f == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "文件不存在");
        }
        return f;
    }

    /**
     * 获取预签名下载 URL
     *
     * @param id            文件 ID
     * @param expireSeconds URL 有效期（秒），为 null 时使用默认值
     * @return 预签名下载 URL
     * @throws BizException 当生成预签名 URL 失败时抛出
     */
    @Override
    public String getPresignedUrl(String id, Integer expireSeconds) {
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

    /**
     * 下载文件字节流
     *
     * @param id 文件 ID
     * @return 文件输入流
     * @throws Exception 下载过程中发生异常
     */
    @Override
    @Transactional(readOnly = true)
    public InputStream download(String id) throws Exception {
        FileDO f = getById(id);
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(f.getBucket())
                .object(f.getFilePath())
                .build());
    }

    /**
     * 按业务查询文件
     *
     * @param bizType 业务类型
     * @param bizId   业务单据 ID
     * @return 文件元信息列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<FileDO> listByBiz(String bizType, String bizId) {
        return fileMapper.selectByBiz(bizType, bizId);
    }

    /**
     * 分页查询文件
     *
     * @param page    页码
     * @param size    每页大小
     * @param bizType 业务类型（可选）
     * @param bizId   业务单据 ID（可选）
     * @param keyword 关键词（可选）
     * @return 分页结果
     */
    @Override
    @Transactional(readOnly = true)
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

    /**
     * 确保 Bucket 存在，不存在则创建
     *
     * @param bucket Bucket 名称
     * @throws Exception 检查或创建过程中发生异常
     */
    private void ensureBucket(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("[File] 创建 bucket: {}", bucket);
        }
    }

    /**
     * 从 MinIO 删除对象（失败仅记录日志，不抛异常）
     *
     * @param f 文件元信息
     * @throws Exception 删除过程中发生异常
     */
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

    /**
     * 计算字节流的 SHA-256 哈希（十六进制字符串）
     *
     * @param content 文件内容
     * @return SHA-256 哈希值，失败时返回 MD5
     */
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

    /**
     * 清洗文件名，替换非法字符
     *
     * @param name 原始文件名
     * @return 清洗后的文件名
     */
    private String sanitizeName(String name) {
        if (!StringUtils.hasText(name)) {
            return "file";
        }
        // 简单清洗：保留 ASCII 字母数字、中文、点、横线、下划线
        return name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
    }
}
