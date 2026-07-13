package com.njydsz.pmis.common.file.config;

import com.njydsz.pmis.common.file.health.FileHealthIndicator;
import com.njydsz.pmis.common.file.service.DedupCleanupScheduler;
import com.njydsz.pmis.common.file.service.FileDedupService;
import com.njydsz.pmis.common.file.storage.CheckpointService;
import com.njydsz.pmis.common.file.storage.CheckpointStore;
import com.njydsz.pmis.common.file.storage.DelegatingCheckpointStore;
import com.njydsz.pmis.common.file.storage.DelegatingMultipartContextStore;
import com.njydsz.pmis.common.file.storage.DefaultCheckpointService;
import com.njydsz.pmis.common.file.storage.IFileStorageProvider;
import com.njydsz.pmis.common.file.storage.IStorageFactory;
import com.njydsz.pmis.common.file.storage.LocalCheckpointStore;
import com.njydsz.pmis.common.file.storage.MultipartContextStore;
import com.njydsz.pmis.common.file.storage.RedisCheckpointStore;
import com.njydsz.pmis.common.file.storage.RedisMultipartContextStore;
import com.njydsz.pmis.common.file.storage.UploadConcurrencyGuard;
import com.njydsz.pmis.common.file.util.FileTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.Collections;

/**
 * 文件存储自动配置类
 *
 * <p>基于 Spring Boot AutoConfiguration 机制，自动装配文件存储相关的 Bean，
 * 包括存储工厂、分片上传上下文存储、检查点存储、健康检查等组件。
 *
 * <p><b>主要特性：</b>
 * <ul>
 *   <li>智能降级：Redis 可用时优先使用 Redis 存储，否则降级到内存或本地文件</li>
 *   <li>并发控制：可选的上传并发保护器，防止同一文件被重复上传</li>
 *   <li>健康检查：集成 Spring Boot Actuator，自动暴露文件存储健康状态</li>
 *   <li>定时清理：每小时自动清理过期的分片上传上下文</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@AutoConfiguration
@EnableConfigurationProperties({FileProperties.class, FileUploadProperties.class})
@ConditionalOnProperty(prefix = "ydsz.file", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileConfiguration {

    /** 分片上传上下文过期时间（分钟） */
    private static final int MULTIPART_CONTEXT_TIMEOUT_MINUTES = 60;

    /** 分片上传上下文存储（由 Spring 自动注入） */
    private MultipartContextStore multipartContextStore;

    private static final Logger configLog = LoggerFactory.getLogger(FileConfiguration.class);

    /**
     * 分片上传上下文存储 Bean
     * <p>当 Redis 可用时优先使用 Redis 存储，否则降级到内存 Map。
     */
    @Bean
    @ConditionalOnMissingBean(MultipartContextStore.class)
    public MultipartContextStore multipartContextStore(
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        StringRedisTemplate template = stringRedisTemplateProvider.getIfAvailable();
        if (template != null) {
            return new DelegatingMultipartContextStore(
                    new RedisMultipartContextStore(template), null);
        }
        configLog.warn("[FileConfig] Redis not available, falling back to InMemoryMultipartContextStore. " +
                "Multi-instance deployments may fail to share multipart upload contexts.");
        return new com.njydsz.pmis.common.file.storage.InMemoryMultipartContextStore();
    }

    /**
     * 检查点存储 Bean
     * <p>当 Redis 可用时优先使用 Redis 存储，否则降级到本地文件。
     */
    @Bean
    @ConditionalOnMissingBean(CheckpointStore.class)
    public CheckpointStore checkpointStore(
            FileProperties fileProperties,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        StringRedisTemplate template = stringRedisTemplateProvider.getIfAvailable();
        CheckpointStore fallback = new LocalCheckpointStore(fileProperties.getCheckpointDir());
        if (template != null) {
            return new DelegatingCheckpointStore(
                    new RedisCheckpointStore(template), fallback);
        }
        configLog.warn("[FileConfig] Redis not available, falling back to LocalCheckpointStore. " +
                "Multi-instance deployments may fail to share checkpoint data for resumable uploads.");
        return fallback;
    }

    /**
     * 检查点服务 Bean
     * <p>基于 CheckpointStore 构建服务层封装。
     */
    @Bean
    @ConditionalOnMissingBean(CheckpointService.class)
    public CheckpointService checkpointService(
            CheckpointStore store,
            MultipartContextStore multipartStore) {
        final long checkpointTtl = 24 * 3600L;
        return new DefaultCheckpointService(store,
                (bucketName, objectName, uploadId) -> {
                    // 在 Bean 初始化阶段无法获取 IFileStorageProvider，返回空列表
                    // 实际运行时由 AbstractFileStorage 重新创建时使用真实 listParts
                    return Collections.emptyList();
                },
                checkpointTtl);
    }

    /**
     * 创建文件存储提供者工厂
     * <p>根据配置初始化 Magic Number 校验开关，创建存储工厂并注入分布式存储实现和并发保护器。
     *
     * @param fileProperties              文件存储基础配置
     * @param fileUploadProperties        文件上传配置
     * @param multipartContextStore       分片上传上下文存储
     * @param checkpointStore             检查点存储
     * @param checkpointService           检查点服务
     * @param stringRedisTemplateProvider Redis 模板提供者（可选）
     * @return 文件存储提供者实例
     */
    @Bean
    @ConditionalOnMissingBean(IFileStorageProvider.class)
    public IFileStorageProvider fileStorageProvider(FileProperties fileProperties,
                                                     FileUploadProperties fileUploadProperties,
                                                     MultipartContextStore multipartContextStore,
                                                     CheckpointStore checkpointStore,
                                                     CheckpointService checkpointService,
                                                     ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        // 从配置文件初始化 Magic Number 校验开关
        FileTypeValidator.init(fileProperties.isCheckMagicNumber());

        IStorageFactory factory = new IStorageFactory(fileProperties, fileUploadProperties);
        // 通知所有已注册的存储实例使用分布式存储
        factory.setMultipartContextStore(multipartContextStore);
        factory.setCheckpointStore(checkpointStore);
        // 注入服务层
        factory.setCheckpointService(checkpointService);

        // 注入并发上传保护器（仅当 Redis 可用且配置启用时）
        UploadConcurrencyGuard guard = buildConcurrencyGuardIfEnabled(fileProperties, stringRedisTemplateProvider.getIfAvailable());
        if (guard != null) {
            factory.setConcurrencyGuard(guard);
        }

        return factory;
    }

    /**
     * 构建并发上传保护器（Redis 不可用或配置未启用时返回 null）
     */
    private UploadConcurrencyGuard buildConcurrencyGuardIfEnabled(FileProperties fileProperties, StringRedisTemplate redisTemplate) {
        if (redisTemplate == null) {
            return null;
        }
        var config = fileProperties.getConcurrencyControl();
        if (config == null || !config.isEnabled()) {
            return null;
        }
        return new UploadConcurrencyGuard(redisTemplate, config);
    }

    /**
     * 创建文件存储健康检查指示器
     *
     * @param fileStorageProvider 文件存储提供者
     * @param fileProperties      文件存储配置
     * @return 文件健康检查指示器实例
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    @ConditionalOnMissingBean(FileHealthIndicator.class)
    public FileHealthIndicator storageHealthIndicator(IFileStorageProvider fileStorageProvider,
                                                      FileProperties fileProperties) {
        return new FileHealthIndicator(fileStorageProvider, fileProperties);
    }

    /**
     * 去重清理调度器 Bean
     * <p>仅当 FileDedupService 存在时注册，定时清理过期的去重映射记录。
     */
    @Bean
    @ConditionalOnBean(FileDedupService.class)
    public DedupCleanupScheduler dedupCleanupScheduler(FileDedupService fileDedupService) {
        return new DedupCleanupScheduler(fileDedupService);
    }

    /**
     * 定时清理过期的分片上传上下文（每小时执行一次）
     */
    @Scheduled(fixedRate = 3_600_000)
    public void cleanExpiredMultipartContexts() {
        if (multipartContextStore == null) return;
        multipartContextStore.cleanExpired(MULTIPART_CONTEXT_TIMEOUT_MINUTES);
    }
}
