package com.njydsz.pmis.common.file.health;

import com.njydsz.pmis.common.file.config.FileProperties;
import com.njydsz.pmis.common.file.storage.IFileStorage;
import com.njydsz.pmis.common.file.storage.IStorageFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 文件存储健康检查
 *
 * <p>检测当前激活的 FileStorage 是否可用，暴露 /actuator/health/file 端点。
 *
 * <p><b>检测逻辑：</b>
 * <ul>
 *   <li>获取当前激活的存储工厂</li>
 *   <li>执行 bucket 存在性检测验证连接</li>
 *   <li>返回存储类型与响应耗时</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Slf4j
@Configuration
@ConditionalOnClass(IStorageFactory.class)
@ConditionalOnProperty(prefix = "remi.file", name = "enabled", havingValue = "true", matchIfMissing = false)
public class FileStorageHealthIndicator implements HealthIndicator {

    /** 文件存储配置 */
    @Resource
    private FileProperties fileProperties;

    /** 存储工厂实例 */
    @Resource
    private IStorageFactory storageFactory;

    /**
     * 执行文件存储健康检查
     * <p>检测当前激活的存储桶是否可达，返回存储类型、桶名、端点和响应耗时等详情。
     *
     * @return 健康检查结果
     */
    @Override
    public Health health() {
        try {
            long startTime = System.currentTimeMillis();

            IFileStorage storage = storageFactory.getStorage();
            boolean reachable = storage.bucketExists(fileProperties.getBucket());
            long responseTime = System.currentTimeMillis() - startTime;

            if (reachable) {
                return Health.up()
                        .withDetail("storageType", fileProperties.getType())
                        .withDetail("bucket", fileProperties.getBucket())
                        .withDetail("endpoint", fileProperties.getEndpoint())
                        .withDetail("responseTimeMs", responseTime)
                        .build();
            }

            return Health.down()
                    .withDetail("storageType", fileProperties.getType())
                    .withDetail("bucket", fileProperties.getBucket())
                    .withDetail("reason", "bucket not accessible")
                    .build();
        } catch (Exception e) {
            log.error("文件存储健康检查失败", e);
            return Health.down()
                    .withDetail("storageType", fileProperties.getType())
                    .withDetail("bucket", fileProperties.getBucket())
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
