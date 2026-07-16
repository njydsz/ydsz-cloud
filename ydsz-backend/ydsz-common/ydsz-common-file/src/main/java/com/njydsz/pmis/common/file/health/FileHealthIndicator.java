package com.njydsz.common.file.health;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.file.config.FileProperties;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.common.util.string.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 存储后端健康检查指示器
 * <p>注册到 Spring Boot Actuator 健康端点（/actuator/health），
 * 检查存储连接可用性与 bucket 是否存在。
 *
 * <p>仅在引入 spring-boot-actuator 依赖时生效（通过 @ConditionalOnClass 控制）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
public class FileHealthIndicator implements HealthIndicator {

    private final IFileStorageProvider fileStorageProvider;
    private final FileProperties fileProperties;

    @Override
    public Health health() {
        try {
            IFileStorage storage = fileStorageProvider.getStorage();
            String bucketName = fileProperties.getBucket();
            // 未配置 bucket 时仅返回存储类型信息，不执行探测
            if (StringUtils.isBlank(bucketName)) {
                return Health.up()
                        .withDetail("storageType", storage.getClass().getSimpleName())
                        .withDetail("bucketConfigured", false)
                        .build();
            }
            // 探测 bucket 是否存在
            boolean bucketExists = storage.bucketExists(bucketName);
            return Health.up()
                    .withDetail("storageType", storage.getClass().getSimpleName())
                    .withDetail("bucketExists", bucketExists)
                    .withDetail("bucketConfigured", true)
                    .build();
        } catch (Exception e) {
            log.warn("[StorageHealthIndicator] storage health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
